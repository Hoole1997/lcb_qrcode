@file:Suppress("RedundantNullableInit")

package com.android.common.bill.ads.pangle

import android.content.Context
import androidx.fragment.app.FragmentActivity
import com.bytedance.sdk.openadsdk.api.interstitial.PAGInterstitialAd
import com.bytedance.sdk.openadsdk.api.interstitial.PAGInterstitialAdInteractionCallback
import com.bytedance.sdk.openadsdk.api.interstitial.PAGInterstitialAdLoadCallback
import com.bytedance.sdk.openadsdk.api.interstitial.PAGInterstitialRequest
import com.bytedance.sdk.openadsdk.api.model.PAGAdEcpmInfo
import com.bytedance.sdk.openadsdk.api.model.PAGErrorModel
import com.bytedance.sdk.openadsdk.api.model.PAGRevenueInfo
import net.corekit.core.ads.RevenueAdData
import net.corekit.core.ads.RevenueAdManager
import net.corekit.core.ads.RevenueInfo
import net.corekit.core.ext.DataStoreIntDelegate
import net.corekit.core.report.ReportDataManager
import com.android.common.bill.BillConfig
import com.android.common.bill.ads.AdException
import com.android.common.bill.ads.AdResult
import com.android.common.bill.ads.admob.AdmobFullScreenNativeAdController
import com.android.common.bill.ads.config.AdConfigManager
import com.android.common.bill.ads.config.AdPlatform
import com.android.common.bill.ads.config.AdType
import com.android.common.bill.ads.protection.AdClickProtectionController
import com.android.common.bill.ads.log.AdLogger
import com.android.common.bill.ads.tracker.AdEventReporter
import com.android.common.bill.ads.tracker.AdRevenueReporter
import com.android.common.bill.ads.util.AdDestroyManager
import com.android.common.bill.ads.util.AdLifecycleGuard
import com.android.common.bill.ads.util.PositionGet
import com.android.common.bill.ui.admob.AdmobFullScreenNativeAdActivity
import com.android.common.bill.ui.dialog.ADLoadingDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.ceil

/**
 * Pangle插页广告控制器
 * 专门处理插页广告的加载和显示
 * 参考文档: https://www.pangleglobal.com/integration/android-interstitial-ads
 * 
 * 预加载说明：
 * - Pangle SDK会在广告显示或关闭后自动开始新的广告请求（自动预加载后续广告）
 * - 但第一次显示时，如果没有预加载，可能需要等待加载时间
 * - 建议在应用启动时调用preloadAd()预加载第一个广告，以提升首次显示体验
 * - 后续广告由SDK自动处理，无需手动预加载
 */
class PangleInterstitialAdController private constructor() {
    
    // 当前展示的sessionId和isPreload
    private var currentSessionId: String = ""
    private var currentIsPreload: Boolean = false

    // 累积点击统计（持久化）
    private var totalClickCount by DataStoreIntDelegate("pangle_interstitial_ad_total_clicks", 0)

    // 累积关闭统计（持久化）
    private var totalCloseCount by DataStoreIntDelegate("pangle_interstitial_ad_total_close", 0)
    
    // 累积加载次数统计（持久化）
    private var totalLoadCount by DataStoreIntDelegate("pangle_interstitial_ad_total_loads", 0)

    // 累积加载成功次数统计（持久化）
    private var totalLoadSucCount by DataStoreIntDelegate("pangle_interstitial_ad_total_load_suc", 0)
    private var totalLoadFailCount by DataStoreIntDelegate("pangle_interstitial_ad_total_load_fails", 0)
    
    // 累积展示失败次数统计（持久化）
    private var totalShowFailCount by DataStoreIntDelegate("pangle_interstitial_ad_total_show_fails", 0)
    
    // 累积触发统计（持久化）
    private var totalShowTriggerCount by DataStoreIntDelegate("pangle_interstitial_ad_total_show_triggers", 0)
    
    // 累积展示统计（持久化）
    private var totalShowCount by DataStoreIntDelegate("pangle_interstitial_ad_total_shows", 0)
    
    // 插页广告是否正在显示的标识
    private var isShowing: Boolean = false
    
    // 当前正在显示的广告（用于资源释放）
    private var currentShowingAd: PAGInterstitialAd? = null
    
    // 展示结果回调
    private var showContinuation: kotlinx.coroutines.CancellableContinuation<AdResult<Unit>>? = null
    
    companion object {
        private const val TAG = "PangleInterstitialAdController"
        private const val LOAD_TIMEOUT = 7000L // 加载超时时间7秒
        private const val CACHE_EXPIRE_MS = 60 * 60 * 1000L // 1小时过期
        
        @Volatile
        private var INSTANCE: PangleInterstitialAdController? = null
        
        fun getInstance(): PangleInterstitialAdController {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PangleInterstitialAdController().also { INSTANCE = it }
            }
        }
    }
    
    /**
     * 缓存的广告实体
     */
    private data class CachedAdEntry(
        val adUnitId: String,
        val ad: PAGInterstitialAd,
        val cacheTime: Long = System.currentTimeMillis()
    ) {
        fun isExpired(): Boolean {
            return System.currentTimeMillis() - cacheTime > CACHE_EXPIRE_MS || !ad.isReady
        }
    }
    
    // 当前加载的广告请求（推荐作为Activity的成员变量）
    private var currentInterstitialRequest: PAGInterstitialRequest? = null
    
    // 缓存的广告对象
    private var cachedAdEntry: CachedAdEntry? = null
    
    /**
     * 预加载插页广告
     * 建议在应用启动时调用此方法预加载第一个广告，以提升首次显示体验
     * 注意：后续广告由Pangle SDK自动预加载，无需手动调用
     * 
     * @param context 上下文
     * @param adUnitId 广告位ID
     */
    suspend fun preloadAd(context: Context, adUnitId: String? = null): AdResult<Unit> {
        val finalAdUnitId = adUnitId ?: BillConfig.pangle.interstitialId
        
        // 检查当前缓存是否存在且未过期
        val cached = cachedAdEntry
        if (cached != null && !cached.isExpired()) {
            AdLogger.d("Pangle插页广告已有有效缓存且未过期，广告位ID: %s，跳过加载", finalAdUnitId)
            return AdResult.Success(Unit)
        }
        
        return loadAd(context, finalAdUnitId)
    }

    /**
     * 基础广告加载方法
     */
    private suspend fun loadAd(context: Context, adUnitId: String): AdResult<Unit> {
        // 累积加载次数统计
        totalLoadCount++
        AdLogger.d("Pangle插页广告累积加载次数: $totalLoadCount")
        
        val requestId = AdEventReporter.reportStartLoad(AdType.INTERSTITIAL, AdPlatform.PANGLE, adUnitId, totalLoadCount)
        
        return suspendCancellableCoroutine { continuation ->
            val startTime = System.currentTimeMillis()

            // 创建PAGInterstitialRequest对象（推荐作为Activity的成员变量）
            val request = PAGInterstitialRequest(context)
            currentInterstitialRequest = request
            
            // 加载广告并注册回调
            PAGInterstitialAd.loadAd(adUnitId, request, object : PAGInterstitialAdLoadCallback {
                override fun onAdLoaded(ad: PAGInterstitialAd) {
                    val loadTime = System.currentTimeMillis() - startTime
                    AdLogger.d("Pangle插页广告加载成功，广告位ID: %s, 耗时: %dms", adUnitId, loadTime)
                    totalLoadSucCount++
                    
                    AdEventReporter.reportLoaded(AdType.INTERSTITIAL, AdPlatform.PANGLE, adUnitId, totalLoadSucCount, AdPlatform.PANGLE.key, ceil(loadTime / 1000.0).toInt(), requestId)

                    cachedAdEntry = CachedAdEntry(adUnitId, ad)
                    continuation.resume(AdResult.Success(Unit))
                }

                override fun onError(model: PAGErrorModel) {
                    val code = model.errorCode
                    val message = model.errorMessage
                    val loadTime = System.currentTimeMillis() - startTime
                    AdLogger.e("Pangle interstitial ad load failed，广告位ID: %s, 耗时: %dms, 错误码: %d, 错误信息: %s", 
                        adUnitId, loadTime, code, message)
                    
                    totalLoadFailCount++
                    AdEventReporter.reportLoadFail(AdType.INTERSTITIAL, AdPlatform.PANGLE, adUnitId, totalLoadFailCount, AdPlatform.PANGLE.key, ceil(loadTime / 1000.0).toInt(), message, requestId)
                    
                    cachedAdEntry = null
                    continuation.resume(AdResult.Failure(
                        createAdException("Pangle interstitial ad load failed: ${message} (code: ${code})")
                    ))
                }
            })
        }
    }
    
    /**
     * 显示插页广告
     * @param activity Activity上下文
     * @param adUnitId 广告位ID，如果为空则使用默认ID
     * @param ignoreFullNative 是否忽略全屏原生广告
     */
    suspend fun showAd(
        activity: FragmentActivity, 
        adUnitId: String? = null,
        ignoreFullNative: Boolean = false,
        sessionId: String = ""
    ): AdResult<Unit> {
        val finalAdUnitId = adUnitId ?: BillConfig.pangle.interstitialId
        
        // 累积触发统计
        totalShowTriggerCount++
        AdLogger.d("Pangle插页广告累积触发展示次数: $totalShowTriggerCount")
        
        // 生命周期检查：等待 Activity Resume 或取消
        val lifecycleGuard = AdLifecycleGuard.instance
        when (val lifecycleResult = lifecycleGuard.awaitResumeOrCancel(activity)) {
            is AdResult.Failure -> {
                AdLogger.w("Pangle插页广告展示被取消：Activity生命周期不满足")
                totalShowFailCount++
                AdEventReporter.reportShowFail(
                    AdType.INTERSTITIAL,
                    AdPlatform.PANGLE,
                    finalAdUnitId,
                    totalShowFailCount,
                    lifecycleResult.error.message,
                    sessionId = sessionId,
                    isPreload = false
                )
                return lifecycleResult
            }
            else -> { /* continue */ }
        }
        
        // 注册 Activity 销毁时的清理回调
        AdDestroyManager.instance.register(activity) {
            AdLogger.d("Pangle插页广告: Activity销毁，清理展示资源")
            // 销毁正在显示的广告对象
            destroyShowingAd()
            showContinuation?.let {
                if (it.isActive) {
                    totalShowFailCount++
                    AdEventReporter.reportShowFail(AdType.INTERSTITIAL, AdPlatform.PANGLE, finalAdUnitId, totalShowFailCount, "Activity destroyed", sessionId = sessionId, isPreload = false)
                    it.resume(AdResult.Failure(createAdException("Pangle interstitial: Activity destroyed")))
                }
            }
            showContinuation = null
            isShowing = false
        }
        
        return try {
            // 1. 检查缓存是否存在且未过期
            currentSessionId = sessionId
            currentIsPreload = cachedAdEntry?.let { !it.isExpired() } ?: false

            // 检查缓存过期
            if (cachedAdEntry?.isExpired() == true) {
                AdEventReporter.reportTimeoutCache(AdType.INTERSTITIAL, AdPlatform.PANGLE, finalAdUnitId)
            }

            val cached = cachedAdEntry
            if (cached == null || cached.isExpired()) {
                AdLogger.d("当前没有广告或已过期，立即加载Pangle插页广告，广告位ID: %s", finalAdUnitId)
                val loadResult = loadAd(activity, finalAdUnitId)
                if (loadResult is AdResult.Failure) {
                    totalShowFailCount++
                    AdEventReporter.reportShowFail(AdType.INTERSTITIAL, AdPlatform.PANGLE, finalAdUnitId, totalShowFailCount, loadResult.error.message, sessionId = currentSessionId, isPreload = currentIsPreload)
                    return loadResult
                }
            }

            val entry = cachedAdEntry
            if (entry != null && !entry.isExpired()) {
                AdLogger.d("显示Pangle插页广告，广告位ID: %s", finalAdUnitId)

                // 2. 显示广告
                val result = showAdInternal(activity, entry.ad, finalAdUnitId, sessionId = currentSessionId, isPreload = currentIsPreload)
                
                // 清空当前广告，Pangle SDK会自动加载下一个
                cachedAdEntry = null
                
                result
            } else {
                totalShowFailCount++
                AdEventReporter.reportShowFail(AdType.INTERSTITIAL, AdPlatform.PANGLE, finalAdUnitId, totalShowFailCount, "No ad available", sessionId = currentSessionId, isPreload = currentIsPreload)
                AdResult.Failure(createAdException("Pangle interstitial ad no available ad"))
            }
        } catch (e: Exception) {
            AdLogger.e("显示Pangle插页广告异常", e)
            totalShowFailCount++
            AdEventReporter.reportShowFail(AdType.INTERSTITIAL, AdPlatform.PANGLE, finalAdUnitId, totalShowFailCount, e.message.orEmpty(), sessionId = currentSessionId, isPreload = currentIsPreload)
            AdResult.Failure(createAdException("Pangle interstitial: show exception: ${e.message}", e))
        }
    }
    
    /**
     * 显示广告的内部实现
     */
    @Suppress("RedundantNullableInit")
    private suspend fun showAdInternal(
        activity: FragmentActivity, 
        interstitialAd: PAGInterstitialAd, 
        adUnitId: String,
        sessionId: String = "",
        isPreload: Boolean = false
    ): AdResult<Unit> {
        // 记录当前显示的广告（用于资源释放）
        currentShowingAd = interstitialAd
        
        val applicationContext = activity.applicationContext
        return suspendCancellableCoroutine { continuation ->
            // 保存 continuation 用于生命周期清理
            showContinuation = continuation
            
            continuation.invokeOnCancellation {
                showContinuation = null
            }
            
            // 临时变量保存收益数据
            var currentRevenueUsd: Double? = null
            var currentCurrency: String? = null
            var currentAdSource: String? = null
            interstitialAd.setAdInteractionListener(object : PAGInterstitialAdInteractionCallback() {
                var currentAdUniqueId = ""
                override fun onAdShowed() {
                    AdLogger.d("Pangle插页广告开始显示")
                    val pagRevenueInfo: PAGRevenueInfo? = interstitialAd.pagRevenueInfo
                    val ecpmInfo: PAGAdEcpmInfo? = pagRevenueInfo?.showEcpm
                    currentCurrency = ecpmInfo?.currency
                    currentAdSource = ecpmInfo?.adnName
                    val currentPlacement = ecpmInfo?.placement
                    val currentRevenueAdUnit = ecpmInfo?.adUnit
                    // Pangle 的 revenue 本身就是美元，直接使用
                    val revenueUsd = ecpmInfo?.revenue?.toDoubleOrNull() ?: 0.0
                    currentRevenueUsd = revenueUsd
                    val impressionValue = revenueUsd

                    // 设置广告正在显示标识
                    isShowing = true

                    // 累积展示统计
                    totalShowCount++
                    AdLogger.d("Pangle插页广告累积展示次数: $totalShowCount")

                    currentAdUniqueId = "${java.util.UUID.randomUUID()}_${ecpmInfo?.adUnit}_${ecpmInfo?.placement}"
                    AdConfigManager.recordShow(AdType.INTERSTITIAL, AdPlatform.PANGLE)
                    AdEventReporter.reportImpression(AdType.INTERSTITIAL, AdPlatform.PANGLE, adUnitId, currentAdUniqueId, totalShowCount, currentAdSource ?: AdPlatform.PANGLE.key, impressionValue, currentCurrency ?: "USD", sessionId = sessionId, isPreload = isPreload)

                    currentRevenueUsd?.let { revenueValue ->
                        AdRevenueReporter.reportRevenue(AdType.INTERSTITIAL, AdPlatform.PANGLE, currentRevenueAdUnit ?: adUnitId, revenueValue, currentCurrency ?: "USD", currentAdSource ?: AdPlatform.PANGLE.key, currentPlacement)
                        // Pangle 的 revenue 本身就是美元，直接使用
                        val revenueUsdLong = revenueValue.toLong()
                        AdLogger.d(
                            "Pangle插页广告收益上报(onShow): adUnit=%s, placement=%s, adn=%s, revenueUsd=%.4f, currency=%s",
                            currentRevenueAdUnit ?: adUnitId,
                            currentPlacement ?: "",
                            currentAdSource ?: AdPlatform.PANGLE.key,
                            revenueValue,
                            currentCurrency ?: ""
                        )
                    }
                }

                override fun onAdClicked() {
                    AdLogger.d("Pangle插页广告被点击")
                    
                    // 累积点击统计
                    totalClickCount++
                    AdLogger.d("Pangle插页广告累积点击次数: $totalClickCount")
                    
                    AdConfigManager.recordClick(AdType.INTERSTITIAL, AdPlatform.PANGLE)

                    // 记录点击用于重复点击保护
                    AdClickProtectionController.recordClick(currentAdUniqueId)
                    
                    val pagRevenueInfo: PAGRevenueInfo? = interstitialAd.pagRevenueInfo
                    val ecpmInfo: PAGAdEcpmInfo? = pagRevenueInfo?.showEcpm
                    val revenueValue = ecpmInfo?.revenue?.toDoubleOrNull() ?: 0.0
                    
                    AdEventReporter.reportClick(AdType.INTERSTITIAL, AdPlatform.PANGLE, adUnitId, currentAdUniqueId, totalClickCount, currentAdSource ?: AdPlatform.PANGLE.key, revenueValue, currentCurrency ?: "USD")
                }

                override fun onAdDismissed() {
                    AdLogger.d("Pangle插页广告关闭")
                    
                    // 设置广告不再显示标识
                    isShowing = false
                    
                    totalCloseCount++
                    
                    val pagRevenueInfo: PAGRevenueInfo? = interstitialAd.pagRevenueInfo
                    val ecpmInfo: PAGAdEcpmInfo? = pagRevenueInfo?.showEcpm
                    val revenueValue = ecpmInfo?.revenue?.toDoubleOrNull() ?: 0.0
                    
                    AdEventReporter.reportClose(AdType.INTERSTITIAL, AdPlatform.PANGLE, adUnitId, totalCloseCount, currentAdSource ?: AdPlatform.PANGLE.key, revenueValue, currentCurrency ?: "USD")
                    
                    // 插页关闭时重新预缓存
                    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                        try {
                            AdLogger.d("Pangle插页广告关闭，开始重新预缓存，广告位ID: %s", adUnitId)
                            preloadAd(applicationContext, adUnitId)
                        } catch (e: Exception) {
                            AdLogger.e("Pangle插页广告重新预缓存失败", e)
                        }
                    }
                    
                    val result = AdResult.Success(Unit)
                    if (continuation.isActive) {
                        continuation.resume(result)
                    }
                }

                override fun onAdShowFailed(error: PAGErrorModel) {
                    super.onAdShowFailed(error)
                    isShowing = false
                    totalShowFailCount++
                    AdLogger.e(
                        "Pangle插页广告show failed: code=%d, message=%s",
                        error.errorCode,
                        error.errorMessage
                    )
                    AdEventReporter.reportShowFail(AdType.INTERSTITIAL, AdPlatform.PANGLE, adUnitId, totalShowFailCount, error.errorMessage.orEmpty(), currentAdSource ?: AdPlatform.PANGLE.key, sessionId = sessionId, isPreload = isPreload)

                    // 展示失败后重新预缓存
                    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                        try {
                            AdLogger.d("Pangle插页广告展示失败，开始重新预缓存，广告位ID: %s", adUnitId)
                            preloadAd(applicationContext, adUnitId)
                        } catch (e: Exception) {
                            AdLogger.e("Pangle插页广告重新预缓存失败", e)
                        }
                    }

                    val result = AdResult.Failure(createAdException("Pangle interstitial: show callback failed: ${error.errorMessage.orEmpty()}"))
                    if (continuation.isActive) {
                        continuation.resume(result)
                    }
                }
            })
            
            if (!interstitialAd.isReady) {
                AdLogger.w("Pangle插页广告未就绪，无法显示")
                totalShowFailCount++
                AdEventReporter.reportShowFail(AdType.INTERSTITIAL, AdPlatform.PANGLE, adUnitId, totalShowFailCount, "interstitial_not_ready", currentAdSource ?: AdPlatform.PANGLE.key, sessionId = sessionId, isPreload = isPreload)
                val result = AdResult.Failure(createAdException("Pangle interstitial ad not ready, isAdReady=false"))
                if (continuation.isActive) {
                    continuation.resume(result)
                }
                return@suspendCancellableCoroutine
            }

            // 显示广告（必须在主线程调用）
            try {
                interstitialAd.show(activity)
            } catch (e: Exception) {
                AdLogger.e("显示Pangle插页广告异常", e)
                totalShowFailCount++
                AdEventReporter.reportShowFail(AdType.INTERSTITIAL, AdPlatform.PANGLE, adUnitId, totalShowFailCount, e.message.orEmpty(), currentAdSource ?: AdPlatform.PANGLE.key, sessionId = sessionId, isPreload = isPreload)
                val result = AdResult.Failure(createAdException("Pangle interstitial: show callback failed: ${e.message}", e))
                if (continuation.isActive) {
                    continuation.resume(result)
                }
            }
        }
    }

    fun hasCachedAd(): Boolean {
        val cached = cachedAdEntry
        return cached != null && !cached.isExpired()
    }

    fun getCurrentAd(): PAGInterstitialAd? = cachedAdEntry?.ad

    /**
     * 销毁广告
     */
    fun destroyAd() {
        cachedAdEntry = null
        currentInterstitialRequest = null
        AdLogger.d("Pangle插页广告已销毁")
    }
    
    /**
     * 销毁正在显示的广告
     * 在Activity销毁或广告关闭时调用，释放正在显示的插页广告资源
     */
    fun destroyShowingAd() {
        currentShowingAd?.let { ad ->
            ad.setAdInteractionListener(null)
            AdLogger.d("Pangle插页广告正在显示的广告已销毁")
        }
        currentShowingAd = null
    }
    
    /**
     * 销毁控制器
     */
    fun destroy() {
        destroyAd()
        AdLogger.d("Pangle插页广告控制器已清理")
    }
    
    /**
     * 获取插页广告是否正在显示的状态
     * @return true 如果插页广告正在显示，false 否则
     */
    fun isAdShowing(): Boolean {
        return isShowing
    }
    
    /**
     * 创建广告异常
     */
    private fun createAdException(message: String, cause: Throwable? = null): AdException {
        return AdException(
            code = 0,
            message = message,
            cause = cause
        )
    }
    
}
