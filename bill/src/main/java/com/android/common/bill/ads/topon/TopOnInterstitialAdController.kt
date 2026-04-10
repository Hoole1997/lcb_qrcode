package com.android.common.bill.ads.topon

import android.content.Context
import androidx.fragment.app.FragmentActivity
import com.android.common.bill.BillConfig
import com.android.common.bill.ads.AdException
import com.android.common.bill.ads.AdResult
import com.android.common.bill.ads.admob.AdmobFullScreenNativeAdController
import com.android.common.bill.ads.config.AdConfigManager
import com.android.common.bill.ads.config.AdPlatform
import com.android.common.bill.ads.config.AdType
import com.android.common.bill.ads.log.AdLogger
import com.android.common.bill.ads.tracker.AdEventReporter
import com.android.common.bill.ads.tracker.AdRevenueReporter
import com.android.common.bill.ads.util.AdDestroyManager
import com.android.common.bill.ads.util.AdLifecycleGuard
import com.android.common.bill.ads.util.PositionGet
import com.android.common.bill.ui.admob.AdmobFullScreenNativeAdActivity
import com.android.common.bill.ui.dialog.ADLoadingDialog
import net.corekit.core.ads.RevenueAdData
import net.corekit.core.ads.RevenueAdManager
import net.corekit.core.ads.RevenueInfo
import net.corekit.core.ext.DataStoreIntDelegate
import net.corekit.core.report.ReportDataManager
import com.android.common.bill.ads.protection.AdClickProtectionController
import com.thinkup.core.api.AdError
import com.thinkup.core.api.TUAdInfo
import com.thinkup.core.api.TUAdRevenueListener
import com.thinkup.interstitial.api.TUInterstitialListener
import com.thinkup.interstitial.api.TUInterstitial
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.ceil

/**
 * TopOn 插页广告控制器
 * 参考 AdMob 插页广告控制器实现，保持埋点一致
 */
class TopOnInterstitialAdController private constructor() {

    // 当前展示的sessionId和isPreload
    private var currentSessionId: String = ""
    private var currentIsPreload: Boolean = false

    // 累积统计（持久化）
    private var totalClickCount by DataStoreIntDelegate("topon_interstitial_ad_total_clicks", 0)
    private var totalCloseCount by DataStoreIntDelegate("topon_interstitial_ad_total_close", 0)
    private var totalLoadCount by DataStoreIntDelegate("topon_interstitial_ad_total_loads", 0)
    private var totalLoadSucCount by DataStoreIntDelegate("topon_interstitial_ad_total_load_suc", 0)
    private var totalLoadFailCount by DataStoreIntDelegate("topon_interstitial_ad_total_load_fails", 0)
    private var totalShowFailCount by DataStoreIntDelegate("topon_interstitial_ad_total_show_fails", 0)
    private var totalShowTriggerCount by DataStoreIntDelegate("topon_interstitial_ad_total_show_triggers", 0)
    private var totalShowCount by DataStoreIntDelegate("topon_interstitial_ad_total_shows", 0)

    // 是否正在展示
    @Volatile
    private var isShowing: Boolean = false
    
    // 当前正在显示的广告（用于资源释放）
    private var currentShowingAd: TUInterstitial? = null
    
    // 展示结果回调
    private var showContinuation: CancellableContinuation<AdResult<Unit>>? = null

    companion object {
        private const val CACHE_EXPIRE_MS = 60 * 60 * 1000L

        @Volatile
        private var INSTANCE: TopOnInterstitialAdController? = null

        fun getInstance(): TopOnInterstitialAdController {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TopOnInterstitialAdController().also { INSTANCE = it }
            }
        }
    }

    private val adCache = mutableMapOf<String, TopOnAdEntry>()

    /**
     * 缓存的广告实体
     */
    private data class TopOnAdEntry(
        val placementId: String,
        val ad: TUInterstitial,
        val listener: TopOnInterstitialListener,
        val cacheTime: Long = System.currentTimeMillis()
    ) {
        fun isExpired(): Boolean {
            return System.currentTimeMillis() - cacheTime > CACHE_EXPIRE_MS || !ad.isAdReady
        }
    }

    /**
     * 预加载广告
     */
    suspend fun preloadAd(context: Context, placementId: String? = null): AdResult<Unit> {

        val finalPlacementId = resolvePlacementId(placementId)
        if (finalPlacementId.isBlank()) {
            AdLogger.w("TopOn插页广告缺少有效的广告位ID，无法预加载")
            return AdResult.Failure(createAdException("placement ID is missing"))
        }

        val cached = synchronized(adCache) {
            adCache[finalPlacementId]?.takeUnless { it.isExpired() }
        }
        if (cached != null) {
            AdLogger.d("TopOn插页广告已有有效缓存，广告位ID: %s", finalPlacementId)
            return AdResult.Success(Unit)
        }

        return when (val loadResult = loadAd(context, finalPlacementId)) {
            is AdResult.Success -> AdResult.Success(Unit)
            is AdResult.Failure -> AdResult.Failure(createAdException("TopOninterstitial ad load returned non-success state"))
        }
    }

    /**
     * 展示广告
     */
    suspend fun showAd(
        activity: FragmentActivity,
        placementId: String? = null,
        ignoreFullNative: Boolean = false,
        sessionId: String = ""
    ): AdResult<Unit> {
        val finalPlacementId = resolvePlacementId(placementId)
        if (finalPlacementId.isBlank()) {
            totalShowFailCount++
            AdEventReporter.reportShowFail(
                AdType.INTERSTITIAL,
                AdPlatform.TOPON,
                placementId.orEmpty(),
                totalShowFailCount,
                "placement ID is missing",
                sessionId = sessionId,
                isPreload = false
            )
            return AdResult.Failure(createAdException("placement ID is missing"))
        }

        totalShowTriggerCount++
        AdLogger.d("TopOn插页广告累积触发展示次数: $totalShowTriggerCount")
        
        // 生命周期检查：等待 Activity Resume 或取消
        val lifecycleGuard = AdLifecycleGuard.instance
        when (val lifecycleResult = lifecycleGuard.awaitResumeOrCancel(activity)) {
            is AdResult.Failure -> {
                AdLogger.w("TopOn插页广告展示被取消：Activity生命周期不满足")
                totalShowFailCount++
                AdEventReporter.reportShowFail(
                    AdType.INTERSTITIAL,
                    AdPlatform.TOPON,
                    finalPlacementId,
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
            AdLogger.d("TopOn插页广告: Activity销毁，清理展示资源")
            // 销毁正在显示的广告对象
            destroyShowingAd()
            showContinuation?.let {
                if (it.isActive) {
                    totalShowFailCount++
                    AdEventReporter.reportShowFail(AdType.INTERSTITIAL, AdPlatform.TOPON, finalPlacementId, totalShowFailCount, "Activity destroyed", sessionId = sessionId, isPreload = false)
                    it.resume(AdResult.Failure(createAdException("TopOn interstitial: Activity destroyed")))
                }
            }
            showContinuation = null
            isShowing = false
        }

        currentSessionId = sessionId
        currentIsPreload = synchronized(adCache) {
            adCache[finalPlacementId]?.takeUnless { it.isExpired() } != null
        }

        // 检查缓存过期
        synchronized(adCache) {
            if (adCache[finalPlacementId]?.isExpired() == true) {
                AdEventReporter.reportTimeoutCache(AdType.INTERSTITIAL, AdPlatform.TOPON, finalPlacementId)
            }
        }

        return try {
            var entry = synchronized(adCache) {
                adCache[finalPlacementId]?.takeUnless { it.isExpired() }
            }

            if (entry == null) {
                when (val loadResult = loadAd(activity, finalPlacementId)) {
                    is AdResult.Success -> {
                        entry = synchronized(adCache) {
                            adCache[finalPlacementId]?.takeUnless { it.isExpired() }
                        }
                    }
                    is AdResult.Failure -> {
                        totalShowFailCount++
                        AdEventReporter.reportShowFail(AdType.INTERSTITIAL, AdPlatform.TOPON, finalPlacementId, totalShowFailCount, loadResult.error.message, sessionId = currentSessionId, isPreload = currentIsPreload)
                        return loadResult
                    }
                }
            }

            if (entry != null && entry.ad.isAdReady) {
                AdLogger.d("TopOn使用缓存插页广告展示，广告位ID: %s", finalPlacementId)
                // 记录当前显示的广告（用于资源释放）
                currentShowingAd = entry.ad
                entry.listener.awaitShow(activity)
            } else {
                totalShowFailCount++
                AdEventReporter.reportShowFail(AdType.INTERSTITIAL, AdPlatform.TOPON, finalPlacementId, totalShowFailCount, "No ad available", sessionId = currentSessionId, isPreload = currentIsPreload)
                AdResult.Failure(createAdException("TopOninterstitial ad no available ad"))
            }
        } catch (e: Exception) {
            AdLogger.e("TopOn插页广告展示异常", e)
            totalShowFailCount++
            AdEventReporter.reportShowFail(AdType.INTERSTITIAL, AdPlatform.TOPON, finalPlacementId, totalShowFailCount, e.message.orEmpty(), sessionId = currentSessionId, isPreload = currentIsPreload)
            AdResult.Failure(createAdException("TopOn interstitial: show exception: ${e.message}", e))
        }
    }

    /**
     * 加载广告
     */
    private suspend fun loadAd(context: Context, placementId: String): AdResult<TopOnAdEntry> {
        totalLoadCount++
        AdLogger.d("TopOn插页广告开始加载，广告位ID: %s，当前累计加载次数: %d", placementId, totalLoadCount)

        val requestId = AdEventReporter.reportStartLoad(AdType.INTERSTITIAL, AdPlatform.TOPON, placementId, totalLoadCount)

        return suspendCancellableCoroutine { continuation ->
            try {
                val applicationContext = context.applicationContext
                val interstitial = TUInterstitial(applicationContext, placementId)
                val listener = TopOnInterstitialListener(
                    placementId = placementId,
                    startLoadTime = System.currentTimeMillis(),
                    interstitial = interstitial,
                    applicationContext = applicationContext,
                    requestId = requestId
                )
                listener.attachLoadContinuation(continuation)

                interstitial.setAdListener(listener)
                interstitial.setAdRevenueListener(listener)

                continuation.invokeOnCancellation {
                    listener.clearLoadContinuation()
                }

                interstitial.load(applicationContext)
            } catch (e: Exception) {
                AdLogger.e("TopOn插页广告load exception", e)
                totalLoadFailCount++
                AdEventReporter.reportLoadFail(AdType.INTERSTITIAL, AdPlatform.TOPON, placementId, totalLoadFailCount, "", 0, e.message.orEmpty(), requestId)
                if (continuation.isActive) {
                    continuation.resume(AdResult.Failure(createAdException("TopOn interstitial ad loadAd exception: ${e.message}", e)))
                }
            }
        }
    }

    /**
     * 销毁广告缓存
     */
    private fun destroyAd() {
        synchronized(adCache) {
            adCache.clear()
        }
        AdLogger.d("TopOn插页广告缓存已清理")
    }

    /**
     * 销毁控制器
     */
    fun destroy() {
        destroyAd()
        AdLogger.d("TopOn插页广告控制器已清理")
    }
    
    /**
     * 销毁正在显示的广告
     * 在Activity销毁或广告关闭时调用，释放正在显示的插页广告资源
     */
    fun destroyShowingAd() {
        currentShowingAd?.let { ad ->
            ad.setAdListener(null)
            ad.setAdRevenueListener(null)
            AdLogger.d("TopOn插页广告正在显示的广告已销毁")
        }
        currentShowingAd = null
    }

    /**
     * 获取插页广告是否正在展示
     */
    fun isAdShowing(): Boolean {
        return isShowing
    }

    /**
     * 获取当前缓存的广告对象（用于竞价）
     */
    fun getCurrentAd(placementId: String? = null): com.thinkup.interstitial.api.TUInterstitial? {
        val finalPlacementId = resolvePlacementId(placementId)
        if (finalPlacementId.isBlank()) {
            return null
        }
        
        return synchronized(adCache) {
            adCache[finalPlacementId]?.takeUnless { it.isExpired() }?.ad
        }
    }

    /**
     * TopOn 插页广告监听器
     */
    private inner class TopOnInterstitialListener(
        private val placementId: String,
        private val startLoadTime: Long,
        private val interstitial: TUInterstitial,
        private val applicationContext: Context,
        private val requestId: String = ""
    ) : TUInterstitialListener, TUAdRevenueListener {

        private var loadContinuation: CancellableContinuation<AdResult<TopOnAdEntry>>? = null
        private var showContinuation: CancellableContinuation<AdResult<Unit>>? = null
        private var lastAdInfo: TUAdInfo? = null
        private var cacheTime: Long = System.currentTimeMillis()

        var currentAdUniqueId = ""

        fun attachLoadContinuation(continuation: CancellableContinuation<AdResult<TopOnAdEntry>>) {
            loadContinuation = continuation
        }

        fun clearLoadContinuation() {
            loadContinuation = null
        }

        private fun resumeLoad(result: AdResult<TopOnAdEntry>) {
            loadContinuation?.let { continuation ->
                if (continuation.isActive) {
                    continuation.resume(result)
                }
            }
            loadContinuation = null
        }

        private fun resumeShow(result: AdResult<Unit>) {
            showContinuation?.let { continuation ->
                if (continuation.isActive) {
                    continuation.resume(result)
                }
            }
            showContinuation = null
        }

        suspend fun awaitShow(activity: FragmentActivity): AdResult<Unit> {
            if (!interstitial.isAdReady) {
                AdLogger.w("TopOn插页ad not ready，展示终止，广告位ID: %s", placementId)
                totalShowFailCount++
                AdEventReporter.reportShowFail(AdType.INTERSTITIAL, AdPlatform.TOPON, placementId, totalShowFailCount, "ad not ready", sessionId = currentSessionId, isPreload = currentIsPreload)
                return AdResult.Failure(createAdException("TopOninterstitial ad not ready, isAdReady=false"))
            }

            return suspendCancellableCoroutine { continuation ->
                showContinuation = continuation
                continuation.invokeOnCancellation {
                    showContinuation = null
                }

                try {
                    interstitial.show(activity)
                } catch (e: Exception) {
                    AdLogger.e("TopOn插页广告调用show异常", e)
                    totalShowFailCount++
                    AdEventReporter.reportShowFail(AdType.INTERSTITIAL, AdPlatform.TOPON, placementId, totalShowFailCount, e.message.orEmpty(), sessionId = currentSessionId, isPreload = currentIsPreload)
                    if (continuation.isActive) {
                        continuation.resume(
                            AdResult.Failure(createAdException("TopOn interstitial: show callback failed: ${e.message}", e))
                        )
                    }
                    showContinuation = null
                }
            }
        }

        override fun onInterstitialAdLoaded() {
            val loadTime = System.currentTimeMillis() - startLoadTime
            totalLoadSucCount++
            cacheTime = System.currentTimeMillis()

            val adInfo = runCatching { interstitial.checkValidAdCaches().firstOrNull() }.getOrNull()

            AdLogger.d(
                "TopOn插页广告加载成功，广告位ID: %s，耗时: %dms，缓存成功次数: %d",
                placementId,
                loadTime,
                totalLoadSucCount
            )

            AdEventReporter.reportLoaded(AdType.INTERSTITIAL, AdPlatform.TOPON, placementId, totalLoadSucCount, adInfo?.networkName.orEmpty(), ceil(loadTime / 1000.0).toInt(), requestId)

            val entry = TopOnAdEntry(
                placementId = placementId,
                ad = interstitial,
                listener = this,
                cacheTime = cacheTime
            )

            synchronized(adCache) {
                adCache[placementId] = entry
            }

            resumeLoad(AdResult.Success(entry))
        }

        override fun onInterstitialAdLoadFail(adError: AdError) {
            val loadTime = System.currentTimeMillis() - startLoadTime
            val errorMsg = adError.desc ?: adError.getFullErrorInfo()
            AdLogger.e(
                "TopOninterstitial ad load failed，广告位ID: %s，耗时: %dms，错误: %s",
                placementId,
                loadTime,
                adError.getFullErrorInfo()
            )

            totalLoadFailCount++
            AdEventReporter.reportLoadFail(AdType.INTERSTITIAL, AdPlatform.TOPON, placementId, totalLoadFailCount, "", ceil(loadTime / 1000.0).toInt(), errorMsg, requestId)

            resumeLoad(AdResult.Failure(AdException(adError.code.toInt(), errorMsg)))
        }

        override fun onInterstitialAdShow(adInfo: TUAdInfo) {
            AdLogger.d("TopOn插页广告开始展示")
            isShowing = true
            lastAdInfo = adInfo
            AdConfigManager.recordShow(AdType.INTERSTITIAL, AdPlatform.TOPON)
        }

        override fun onInterstitialAdClicked(adInfo: TUAdInfo) {
            AdLogger.d("TopOn插页广告被点击")
            totalClickCount++
            lastAdInfo = adInfo
            AdLogger.d("TopOn插页广告累积点击次数: $totalClickCount")

            AdClickProtectionController.recordClick(currentAdUniqueId)
            AdConfigManager.recordClick(AdType.INTERSTITIAL, AdPlatform.TOPON)

            AdEventReporter.reportClick(AdType.INTERSTITIAL, AdPlatform.TOPON, placementId, currentAdUniqueId, totalClickCount, adInfo.networkName ?: "", adInfo.publisherRevenue ?: 0.0, adInfo.currency ?: "")
        }

        override fun onInterstitialAdClose(adInfo: TUAdInfo) {
            AdLogger.d("TopOn插页广告关闭")
            isShowing = false
            totalCloseCount++
            lastAdInfo = adInfo

            AdEventReporter.reportClose(AdType.INTERSTITIAL, AdPlatform.TOPON, placementId, totalCloseCount, adInfo.networkName ?: "", adInfo.publisherRevenue ?: 0.0, adInfo.currency ?: "")

            synchronized(adCache) {
                adCache.remove(placementId)
            }

            // 插页关闭时重新预缓存
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    AdLogger.d("TopOn插页广告关闭，开始重新预缓存，广告位ID: %s", placementId)
                    preloadAd(applicationContext, placementId)
                } catch (e: Exception) {
                    AdLogger.e("TopOn插页广告重新预缓存失败", e)
                }
            }

            resumeShow(AdResult.Success(Unit))
        }

        override fun onInterstitialAdVideoError(adError: AdError) {
            AdLogger.w("TopOn插页广告展示失败: %s", adError.desc ?: adError.getFullErrorInfo())
            isShowing = false
            totalShowFailCount++

            AdEventReporter.reportShowFail(AdType.INTERSTITIAL, AdPlatform.TOPON, placementId, totalShowFailCount, adError.desc ?: adError.getFullErrorInfo(), lastAdInfo?.networkName ?: "", sessionId = currentSessionId, isPreload = currentIsPreload)

            synchronized(adCache) {
                adCache.remove(placementId)
            }

            // 展示失败后重新预缓存
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    AdLogger.d("TopOn插页广告展示失败，开始重新预缓存，广告位ID: %s", placementId)
                    preloadAd(applicationContext, placementId)
                } catch (e: Exception) {
                    AdLogger.e("TopOn插页广告重新预缓存失败", e)
                }
            }

            resumeShow(AdResult.Failure(createAdException("TopOn interstitial: show callback failed: ${adError.desc ?: "unknown"}")))
        }

        override fun onInterstitialAdVideoStart(adInfo: TUAdInfo) {
            // 无需额外处理
        }

        override fun onInterstitialAdVideoEnd(adInfo: TUAdInfo) {
            // 无需额外处理
        }

        override fun onAdRevenuePaid(adInfo: TUAdInfo) {
            lastAdInfo = adInfo
            totalShowCount++
            AdLogger.d(
                "TopOn插页广告收益回调，value=${adInfo.publisherRevenue ?: adInfo.ecpm}, currency=${adInfo.currency}"
            )

            currentAdUniqueId = "${java.util.UUID.randomUUID()}_${adInfo.placementId}_${adInfo.adsourceId}"

            AdEventReporter.reportImpression(AdType.INTERSTITIAL, AdPlatform.TOPON, placementId, currentAdUniqueId, totalShowCount, adInfo.networkName ?: "", adInfo.publisherRevenue ?: 0.0, adInfo.currency ?: "", sessionId = currentSessionId, isPreload = currentIsPreload)

            val revenueValue = adInfo.publisherRevenue ?: 0.0
            // TopOn 的 revenueValue 已经是美元，不需要转换
            val revenueUsd = revenueValue.toLong()

            AdRevenueReporter.reportRevenue(
                AdType.INTERSTITIAL,
                AdPlatform.TOPON,
                adInfo.placementId ?: "",
                adInfo.publisherRevenue ?: adInfo.ecpm ?: 0.0,
                adInfo.currency ?: "USD",
                adInfo.networkName,
                adInfo.scenarioId ?: adInfo.placementId ?: ""
            )
        }
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

    /**
     * 解析广告位ID
     */
    private fun resolvePlacementId(placementId: String?): String {
        if (!placementId.isNullOrBlank()) {
            return placementId
        }

        return BillConfig.topon.interstitialId
    }
}
