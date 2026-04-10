package com.android.common.bill.ads.pangle

import android.content.Context
import android.view.ViewGroup
import androidx.lifecycle.LifecycleOwner
import com.bytedance.sdk.openadsdk.api.model.PAGAdEcpmInfo
import com.bytedance.sdk.openadsdk.api.model.PAGErrorModel
import com.bytedance.sdk.openadsdk.api.model.PAGRevenueInfo
import com.bytedance.sdk.openadsdk.api.nativeAd.PAGNativeAd
import com.bytedance.sdk.openadsdk.api.nativeAd.PAGNativeAdInteractionCallback
import com.bytedance.sdk.openadsdk.api.nativeAd.PAGNativeAdLoadCallback
import com.bytedance.sdk.openadsdk.api.nativeAd.PAGNativeRequest
import com.android.common.bill.BillConfig
import com.android.common.bill.ads.AdException
import com.android.common.bill.ads.AdResult
import com.android.common.bill.ads.config.AdConfigManager
import com.android.common.bill.ads.config.AdPlatform
import com.android.common.bill.ads.config.AdType
import com.android.common.bill.ads.protection.AdClickProtectionController
import com.android.common.bill.ads.log.AdLogger
import com.android.common.bill.ads.tracker.AdEventReporter
import com.android.common.bill.ads.tracker.AdRevenueReporter
import net.corekit.core.ads.RevenueAdData
import net.corekit.core.ads.RevenueAdManager
import net.corekit.core.ads.RevenueInfo
import com.android.common.bill.ads.util.AdDestroyManager
import com.android.common.bill.ads.util.PositionGet
import com.android.common.bill.ui.pangle.PangleFullScreenNativeAdView
import net.corekit.core.ext.DataStoreIntDelegate
import net.corekit.core.report.ReportDataManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.ceil
import kotlin.math.roundToLong

/**
 * Pangle全屏原生广告控制器
 * 参考文档：https://www.pangleglobal.com/integration/android-native-ads
 */
class PangleFullScreenNativeAdController private constructor() {

    // 当前展示的sessionId和isPreload
    private var currentSessionId: String = ""
    private var currentIsPreload: Boolean = false
    @Volatile
    private var onAdDisplayedCallback: (() -> Unit)? = null

    // 累积点击/展示等统计（持久化）
    private var totalClickCount by DataStoreIntDelegate("pangle_full_native_total_clicks", 0)
    private var totalCloseCount by DataStoreIntDelegate("pangle_full_native_total_close", 0)
    private var totalLoadCount by DataStoreIntDelegate("pangle_full_native_total_loads", 0)
    private var totalLoadSucCount by DataStoreIntDelegate("pangle_full_native_total_load_suc", 0)
    private var totalLoadFailCount by DataStoreIntDelegate("pangle_full_native_total_load_fails", 0)
    private var totalShowFailCount by DataStoreIntDelegate("pangle_full_native_total_show_fails", 0)
    private var totalShowTriggerCount by DataStoreIntDelegate("pangle_full_native_total_show_triggers", 0)
    private var totalShowCount by DataStoreIntDelegate("pangle_full_native_total_shows", 0)

    private val nativeAdView = PangleFullScreenNativeAdView()
    
    // 全屏原生广告是否正在显示的标识
    private var isShowing: Boolean = false
    
    // 当前正在显示的广告（用于资源释放）
    private var currentShowingAd: PAGNativeAd? = null

    companion object {
        private const val TAG = "PangleFullScreenNative"
        private const val AD_TIMEOUT = 1 * 60 * 60 * 1000L
        private const val DEFAULT_CACHE_SIZE_PER_AD_UNIT = 1

        @Volatile
        private var INSTANCE: PangleFullScreenNativeAdController? = null

        fun getInstance(): PangleFullScreenNativeAdController {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PangleFullScreenNativeAdController().also { INSTANCE = it }
            }
        }
    }

    private data class CachedFullScreenNativeAd(
        val ad: PAGNativeAd,
        val adUnitId: String,
        val loadTime: Long = System.currentTimeMillis()
    ) {
        fun isExpired(): Boolean {
            return System.currentTimeMillis() - loadTime > AD_TIMEOUT || !ad.isReady
        }
    }

    private val adCachePool = mutableListOf<CachedFullScreenNativeAd>()

    fun closeEvent(
        adUnitId: String = "",
        adSource: String? = "Pangle",
        valueUsd: Double? = null,
        currencyCode: String? = null
    ) {
        // 设置广告不再显示标识
        isShowing = false
        onAdDisplayedCallback = null
        totalCloseCount++
        AdEventReporter.reportClose(AdType.FULL_SCREEN_NATIVE, AdPlatform.PANGLE, adUnitId, totalCloseCount, adSource ?: AdPlatform.PANGLE.key, valueUsd ?: 0.0, currencyCode ?: "USD")
    }

    suspend fun preloadAd(context: Context, adUnitId: String? = null): AdResult<Unit> {
        val finalAdUnitId = adUnitId ?: BillConfig.pangle.fullNativeId
        
        // 检查缓存是否有效
        val cached = synchronized(adCachePool) {
            adCachePool.firstOrNull { it.adUnitId == finalAdUnitId && !it.isExpired() }
        }
        if (cached != null) {
            AdLogger.d("Pangle全屏原生广告已有有效缓存，广告位ID: %s", finalAdUnitId)
            return AdResult.Success(Unit)
        }
        
        return loadAdToCache(context, finalAdUnitId)
    }

    suspend fun showAdInContainer(
        context: Context,
        container: ViewGroup,
        lifecycleOwner: LifecycleOwner,
        adUnitId: String? = null,
        sessionId: String = "",
        onAdDisplayed: (() -> Unit)? = null
    ): AdResult<Unit> {
        val finalAdUnitId = adUnitId ?: BillConfig.pangle.fullNativeId

        totalShowTriggerCount++
// 注册 Activity 销毁时的清理回调
        (context as? androidx.fragment.app.FragmentActivity)?.let { activity ->
            AdDestroyManager.instance.register(activity) {
                AdLogger.d("Pangle全屏原生广告: Activity销毁，清理展示资源")
                // 先销毁正在显示的广告对象
                destroyShowingAd()
                // 再移除已添加的广告 View
                container.removeAllViews()
                isShowing = false
            }
        }

        currentSessionId = sessionId
        currentIsPreload = peekCachedAd(finalAdUnitId) != null
        onAdDisplayedCallback = onAdDisplayed

        // 检查缓存过期
        synchronized(adCachePool) {
            if (adCachePool.any { it.adUnitId == finalAdUnitId && it.isExpired() }) {
                AdEventReporter.reportTimeoutCache(AdType.FULL_SCREEN_NATIVE, AdPlatform.PANGLE, finalAdUnitId)
            }
        }

        return try {
            nativeAdView.createFullScreenLoadingView(context, container)

            when (val result = getAd(context, finalAdUnitId)) {
                is AdResult.Success -> {
                    val nativeAd = result.data
                    
                    // 记录当前显示的广告（用于资源释放）
                    currentShowingAd = nativeAd

                    if(!nativeAd.isReady){
                        throw IllegalArgumentException("full_native_not_ready")
                    }

                    var currentRevenueUsd: Double? = null
                    var currentCurrency: String? = null
                    var currentAdSource: String? = null
                    var currentPlacement: String? = null
                    var currentRevenueAdUnit: String? = null

                    val bindSuccess = nativeAdView.bindFullScreenNativeAdToContainer(
                        context = context,
                        container = container,
                        nativeAd = nativeAd,
                        lifecycleOwner = lifecycleOwner,
                        interactionListener = object : PAGNativeAdInteractionCallback() {
                            var currentAdUniqueId = ""
                            override fun onAdShowed() {
                                AdLogger.d("Pangle全屏原生广告开始显示")
                                val pagRevenueInfo: PAGRevenueInfo? = nativeAd.pagRevenueInfo
                                val ecpmInfo: PAGAdEcpmInfo? = pagRevenueInfo?.showEcpm
                                currentCurrency = ecpmInfo?.currency
                                currentAdSource = ecpmInfo?.adnName
                                currentPlacement = ecpmInfo?.placement
                                currentRevenueAdUnit = ecpmInfo?.adUnit
                                // Pangle 的 revenue 本身就是美元，直接使用
                                val revenueUsd = ecpmInfo?.revenue?.toDoubleOrNull() ?: 0.0
                                currentRevenueUsd = revenueUsd
                                val impressionValue = revenueUsd

                                // 设置广告正在显示标识
                                isShowing = true
                                notifyAdDisplayedIfNeeded()
                                
                                totalShowCount++

                                currentAdUniqueId = "${java.util.UUID.randomUUID()}_${ecpmInfo?.adUnit}_${ecpmInfo?.placement}"
                                AdConfigManager.recordShow(AdType.FULL_SCREEN_NATIVE, AdPlatform.PANGLE)

                                AdEventReporter.reportImpression(AdType.FULL_SCREEN_NATIVE, AdPlatform.PANGLE, finalAdUnitId, currentAdUniqueId, totalShowCount, currentAdSource ?: AdPlatform.PANGLE.key, impressionValue, currentCurrency ?: "USD", sessionId = currentSessionId, isPreload = currentIsPreload)

                                currentRevenueUsd?.let { revenueValue ->
                                    AdRevenueReporter.reportRevenue(AdType.FULL_SCREEN_NATIVE, AdPlatform.PANGLE, currentRevenueAdUnit ?: finalAdUnitId, revenueValue, currentCurrency ?: "USD", currentAdSource ?: AdPlatform.PANGLE.key, currentPlacement)
                                    // Pangle 的 revenue 本身就是美元，直接使用
                                    val revenueUsd = ecpmInfo?.revenue?.toDoubleOrNull()?.toLong() ?: 0L
                                    AdLogger.d(
                                        "Pangle全屏原生收益(onShow): adUnit=%s, placement=%s, adn=%s, revenueUsd=%.4f, currency=%s",
                                        currentRevenueAdUnit ?: finalAdUnitId,
                                        currentPlacement ?: "",
                                        currentAdSource ?: AdPlatform.PANGLE.key,
                                        revenueValue,
                                        currentCurrency ?: ""
                                    )
                                }
                                
                                // 异步预加载下一个广告到缓存（如果缓存未满）
                                if (!isCacheFull(finalAdUnitId)) {
                                    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                                        try {
                                            preloadAd(context, finalAdUnitId)
                                        } catch (e: Exception) {
                                            AdLogger.e("Pangle全屏原生广告预加载失败", e)
                                        }
                                    }
                                }
                            }

                            override fun onAdClicked() {
                                AdLogger.d("Pangle全屏原生广告被点击")
                                totalClickCount++
                                AdConfigManager.recordClick(AdType.FULL_SCREEN_NATIVE, AdPlatform.PANGLE)

                                // 记录点击用于重复点击保护
                                AdClickProtectionController.recordClick(currentAdUniqueId)
                                AdEventReporter.reportClick(AdType.FULL_SCREEN_NATIVE, AdPlatform.PANGLE, finalAdUnitId, currentAdUniqueId, totalClickCount, currentAdSource ?: AdPlatform.PANGLE.key, nativeAd.pagRevenueInfo?.showEcpm?.revenue?.toDoubleOrNull() ?: 0.0, currentCurrency ?: "USD")
                            }

                            override fun onAdDismissed() {
                                AdLogger.d("Pangle全屏原生广告关闭")
                                onAdDisplayedCallback = null
                                closeEvent(
                                    adUnitId = finalAdUnitId,
                                    adSource = currentAdSource,
                                    valueUsd = currentRevenueUsd,
                                    currencyCode = currentCurrency
                                )
                            }

                            override fun onAdShowFailed(error: PAGErrorModel) {
                                super.onAdShowFailed(error)
                                onAdDisplayedCallback = null
                                totalShowFailCount++
                                AdLogger.e(
                                    "Pangle全屏原生广告show failed: code=%d, message=%s",
                                    error.errorCode,
                                    error.errorMessage
                                )
                                AdEventReporter.reportShowFail(AdType.FULL_SCREEN_NATIVE, AdPlatform.PANGLE, finalAdUnitId, totalShowFailCount, error.errorMessage.orEmpty(), currentAdSource ?: AdPlatform.PANGLE.key, sessionId = currentSessionId, isPreload = currentIsPreload)
                            }
                        }
                    )

                    if (bindSuccess) {
                        AdResult.Success(Unit)
                    } else {
                        onAdDisplayedCallback = null
                        totalShowFailCount++
                        AdEventReporter.reportShowFail(AdType.FULL_SCREEN_NATIVE, AdPlatform.PANGLE, finalAdUnitId, totalShowFailCount, "bind_failed", sessionId = currentSessionId, isPreload = currentIsPreload)
                        AdResult.Failure(createAdException("Pangle full-screen native: ad bindView failed"))
                    }
                }
                is AdResult.Failure -> {
                    onAdDisplayedCallback = null
                    totalShowFailCount++
                    AdEventReporter.reportShowFail(AdType.FULL_SCREEN_NATIVE, AdPlatform.PANGLE, finalAdUnitId, totalShowFailCount, result.error.message, sessionId = currentSessionId, isPreload = currentIsPreload)
                    AdResult.Failure(result.error)
                }
            }
        } catch (e: Exception) {
            onAdDisplayedCallback = null
            totalShowFailCount++
            AdEventReporter.reportShowFail(AdType.FULL_SCREEN_NATIVE, AdPlatform.PANGLE, finalAdUnitId, totalShowFailCount, e.message.orEmpty(), sessionId = currentSessionId, isPreload = currentIsPreload)
            AdLogger.e("Pangle全屏原生广告展示异常", e)
            AdResult.Failure(createAdException("Pangle full-screen native: show exception: ${e.message}", e))
        }
    }

    private fun notifyAdDisplayedIfNeeded() {
        val callback = onAdDisplayedCallback ?: return
        onAdDisplayedCallback = null
        try {
            callback.invoke()
        } catch (e: Exception) {
            AdLogger.e("Pangle全屏原生 onAdDisplayed 回调异常", e)
        }
    }

    private suspend fun getAd(context: Context, adUnitId: String): AdResult<PAGNativeAd> {
        var cachedAd = getCachedAd(adUnitId)
        if (cachedAd == null) {
            AdLogger.d("缓存为空，立即加载Pangle全屏原生广告，广告位ID: %s", adUnitId)
            loadAdToCache(context, adUnitId)
            cachedAd = getCachedAd(adUnitId)
        }
        return if (cachedAd != null) {
            AdResult.Success(cachedAd.ad)
        } else {
            AdResult.Failure(createAdException("Pangle full-screen native: load returned null"))
        }
    }

    private fun getCachedAd(adUnitId: String): CachedFullScreenNativeAd? {
        synchronized(adCachePool) {
            val index = adCachePool.indexOfFirst { it.adUnitId == adUnitId && !it.isExpired() }
            return if (index != -1) adCachePool.removeAt(index) else null
        }
    }

    private fun peekCachedAd(adUnitId: String): PAGNativeAd? {
        synchronized(adCachePool) {
            return adCachePool.firstOrNull { it.adUnitId == adUnitId && !it.isExpired() }?.ad
        }
    }

    suspend fun loadAdToCache(context: Context, adUnitId: String): AdResult<Unit> {
        return try {
            // 检查缓存是否已满（需要同步访问）
            val currentCount = synchronized(adCachePool) {
                adCachePool.count { it.adUnitId == adUnitId && !it.isExpired() }
            }
            if (currentCount >= DEFAULT_CACHE_SIZE_PER_AD_UNIT) {
                AdLogger.d("广告位 %s 缓存已满", adUnitId)
                return AdResult.Success(Unit)
            }
            val ad = loadAd(context, adUnitId)
            if (ad != null) {
                synchronized(adCachePool) {
                    adCachePool.add(CachedFullScreenNativeAd(ad, adUnitId))
                }
                AdResult.Success(Unit)
            } else {
                AdResult.Failure(createAdException("Pangle full-screen native: load returned null"))
            }
        } catch (e: Exception) {
            AdLogger.e("Pangle全屏原生广告缓存load exception", e)
            AdResult.Failure(createAdException("Pangle full-screen native ad loadAdToCache exception: ${e.message}", e))
        }
    }

    fun getCurrentAd(adUnitId: String? = null): PAGNativeAd? {
        val finalAdUnitId = adUnitId ?: BillConfig.pangle.fullNativeId
        return peekCachedAd(finalAdUnitId)
    }

    /**
     * 检查指定广告位缓存是否已满
     */
    private fun isCacheFull(adUnitId: String): Boolean {
        synchronized(adCachePool) {
            return adCachePool.count { it.adUnitId == adUnitId && !it.isExpired() } >= DEFAULT_CACHE_SIZE_PER_AD_UNIT
        }
    }

    private suspend fun loadAd(context: Context, adUnitId: String): PAGNativeAd? {
        totalLoadCount++
        val requestId = AdEventReporter.reportStartLoad(AdType.FULL_SCREEN_NATIVE, AdPlatform.PANGLE, adUnitId, totalLoadCount)

        return suspendCancellableCoroutine { continuation ->
            val startTime = System.currentTimeMillis()
            val request = PAGNativeRequest(context)
            PAGNativeAd.loadAd(adUnitId, request, object : PAGNativeAdLoadCallback {
                override fun onAdLoaded(ad: PAGNativeAd) {
                    val loadTime = System.currentTimeMillis() - startTime
                    AdLogger.d("Pangle全屏原生广告加载成功，广告位ID: %s, 耗时: %dms", adUnitId, loadTime)
                    totalLoadSucCount++
                    AdEventReporter.reportLoaded(AdType.FULL_SCREEN_NATIVE, AdPlatform.PANGLE, adUnitId, totalLoadSucCount, AdPlatform.PANGLE.key, ceil(loadTime / 1000.0).toInt(), requestId)
                    continuation.resume(ad)
                }

                override fun onError(model: PAGErrorModel) {
                    val code = model.errorCode
                    val message = model.errorMessage
                    val loadTime = System.currentTimeMillis() - startTime
                    AdLogger.e("Pangle全屏原生ad load failed，广告位ID: %s, 耗时: %dms, 错误码: %d, 错误信息: %s", adUnitId, loadTime, code, message)
                    totalLoadFailCount ++
                    AdEventReporter.reportLoadFail(AdType.FULL_SCREEN_NATIVE, AdPlatform.PANGLE, adUnitId, totalLoadFailCount, AdPlatform.PANGLE.key, ceil(loadTime / 1000.0).toInt(), message, requestId)
                    continuation.resume(null)
                }
            })
        }
    }

    fun hasCachedAd(adUnitId: String? = null): Boolean {
        synchronized(adCachePool) {
            return if (adUnitId != null) {
                adCachePool.any { it.adUnitId == adUnitId && !it.isExpired() }
            } else {
                adCachePool.any { !it.isExpired() }
            }
        }
    }

    private fun createAdException(message: String, cause: Throwable? = null): AdException {
        return AdException(
            code = -1,
            message = message,
            cause = cause
        )
    }

    fun destroy() {
        synchronized(adCachePool) {
            adCachePool.clear()
        }
        AdLogger.d("Pangle全屏原生广告控制器已清理")
    }
    
    /**
     * 销毁正在显示的广告
     * 在Activity销毁或广告关闭时调用，释放正在显示的全屏原生广告资源
     */
    fun destroyShowingAd() {
        currentShowingAd?.let { ad ->
            ad.unregisterView()
            AdLogger.d("Pangle全屏原生广告正在显示的广告已销毁")
        }
        currentShowingAd = null
    }
    
    /**
     * 获取全屏原生广告是否正在显示的状态
     * @return true 如果全屏原生广告正在显示，false 否则
     */
    fun isAdShowing(): Boolean {
        return isShowing
    }
}
