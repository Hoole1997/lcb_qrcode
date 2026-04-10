package com.android.common.bill.ads.topon

import android.content.Context
import androidx.fragment.app.FragmentActivity
import com.android.common.bill.BillConfig
import com.android.common.bill.ads.AdException
import com.android.common.bill.ads.AdResult
import com.android.common.bill.ads.config.AdConfigManager
import com.android.common.bill.ads.config.AdPlatform
import com.android.common.bill.ads.config.AdType
import com.android.common.bill.ads.log.AdLogger
import com.android.common.bill.ads.tracker.AdEventReporter
import com.android.common.bill.ads.tracker.AdRevenueReporter
import com.android.common.bill.ads.protection.AdClickProtectionController
import com.android.common.bill.ads.util.AdDestroyManager
import com.android.common.bill.ads.util.AdLifecycleGuard
import com.android.common.bill.ads.util.PositionGet
import com.android.common.bill.ui.dialog.ADLoadingDialog
import net.corekit.core.ads.RevenueAdData
import net.corekit.core.ads.RevenueAdManager
import net.corekit.core.ads.RevenueInfo
import net.corekit.core.ext.DataStoreIntDelegate
import net.corekit.core.report.ReportDataManager
import com.android.common.bill.ads.PreloadController
import com.thinkup.core.api.AdError
import com.thinkup.core.api.TUAdInfo
import com.thinkup.core.api.TUAdRevenueListener
import com.thinkup.rewardvideo.api.TURewardVideoAd
import com.thinkup.rewardvideo.api.TURewardVideoListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.ceil

/**
 * TopOn 激励广告控制器
 * 参考 AdMob 激励广告控制器实现，保持埋点和收益上报一致
 */
class TopOnRewardedAdController private constructor() {

    // 当前展示的sessionId和isPreload
    private var currentSessionId: String = ""
    private var currentIsPreload: Boolean = false

    // 累积统计（持久化）
    private var totalClickCount by DataStoreIntDelegate("topon_rewarded_ad_total_clicks", 0)
    private var totalCloseCount by DataStoreIntDelegate("topon_rewarded_ad_total_close", 0)
    private var totalLoadCount by DataStoreIntDelegate("topon_rewarded_ad_total_loads", 0)
    private var totalLoadSucCount by DataStoreIntDelegate("topon_rewarded_ad_total_load_suc", 0)
    private var totalLoadFailCount by DataStoreIntDelegate("topon_rewarded_ad_total_load_fails", 0)
    private var totalShowFailCount by DataStoreIntDelegate("topon_rewarded_ad_total_show_fails", 0)
    private var totalShowTriggerCount by DataStoreIntDelegate("topon_rewarded_ad_total_show_triggers", 0)
    private var totalShowCount by DataStoreIntDelegate("topon_rewarded_ad_total_shows", 0)
    private var totalRewardEarnedCount by DataStoreIntDelegate("topon_rewarded_ad_total_reward_earned", 0)

    // 是否正在展示
    @Volatile
    private var isShowing: Boolean = false
    
    // 当前正在显示的广告（用于资源释放）
    private var currentShowingAd: TURewardVideoAd? = null
    
    // 展示结果回调
    private var showContinuation: kotlinx.coroutines.CancellableContinuation<AdResult<Unit>>? = null
    
    // 奖励回调
    private var rewardCallback: ((String, Int) -> Unit)? = null

    companion object {
        private const val CACHE_EXPIRE_MS = 60 * 60 * 1000L

        @Volatile
        private var INSTANCE: TopOnRewardedAdController? = null

        fun getInstance(): TopOnRewardedAdController {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TopOnRewardedAdController().also { INSTANCE = it }
            }
        }
    }

    private val adCache = mutableMapOf<String, TopOnRewardedAdEntry>()

    /**
     * 缓存的激励广告实体
     */
    private data class TopOnRewardedAdEntry(
        val placementId: String,
        val ad: TURewardVideoAd,
        val listener: TopOnRewardedVideoListener,
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
            AdLogger.w("TopOn激励广告缺少有效的广告位ID，无法预加载")
            return AdResult.Failure(createAdException("placement ID is missing"))
        }

        val cached = synchronized(adCache) {
            adCache[finalPlacementId]?.takeUnless { it.isExpired() }
        }
        if (cached != null) {
            AdLogger.d("TopOn激励广告已有有效缓存，广告位ID: %s", finalPlacementId)
            return AdResult.Success(Unit)
        }

        return when (val loadResult = loadAd(context, finalPlacementId)) {
            is AdResult.Success -> AdResult.Success(Unit)
            is AdResult.Failure -> AdResult.Failure(createAdException("TopOnrewarded ad load returned non-success state"))
        }
    }

    /**
     * 展示广告
     */
    suspend fun showAd(
        activity: FragmentActivity,
        placementId: String? = null,
        onRewardEarned: ((String, Int) -> Unit)? = null,
        sessionId: String = ""
    ): AdResult<Unit> {
        val finalPlacementId = resolvePlacementId(placementId)
        if (finalPlacementId.isBlank()) {
            totalShowFailCount++
            AdEventReporter.reportShowFail(
                AdType.REWARDED,
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
        AdLogger.d("TopOn激励广告累积触发展示次数: $totalShowTriggerCount")
        
        // 生命周期检查：等待 Activity Resume 或取消
        val lifecycleGuard = AdLifecycleGuard.instance
        when (val lifecycleResult = lifecycleGuard.awaitResumeOrCancel(activity)) {
            is AdResult.Failure -> {
                AdLogger.w("TopOn激励广告展示被取消：Activity生命周期不满足")
                totalShowFailCount++
                AdEventReporter.reportShowFail(
                    AdType.REWARDED,
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
            AdLogger.d("TopOn激励广告: Activity销毁，清理展示资源")
            // 销毁正在显示的广告对象
            destroyShowingAd()
            showContinuation?.let {
                if (it.isActive) {
                    totalShowFailCount++
                    AdEventReporter.reportShowFail(AdType.REWARDED, AdPlatform.TOPON, finalPlacementId, totalShowFailCount, "Activity destroyed", sessionId = sessionId, isPreload = false)
                    it.resume(AdResult.Failure(createAdException("TopOn rewarded: Activity destroyed")))
                }
            }
            showContinuation = null
            rewardCallback = null
            isShowing = false
        }



        // 拦截器检查
        currentSessionId = sessionId
        currentIsPreload = synchronized(adCache) {
            adCache[finalPlacementId]?.takeUnless { it.isExpired() } != null
        }

        // 检查缓存过期
        synchronized(adCache) {
            if (adCache[finalPlacementId]?.isExpired() == true) {
                AdEventReporter.reportTimeoutCache(AdType.REWARDED, AdPlatform.TOPON, finalPlacementId)
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
                        AdEventReporter.reportShowFail(AdType.REWARDED, AdPlatform.TOPON, finalPlacementId, totalShowFailCount, loadResult.error.message, sessionId = currentSessionId, isPreload = currentIsPreload)
                        return loadResult
                    }
                }
            }

            if (entry != null && entry.ad.isAdReady) {
                AdLogger.d("TopOn使用缓存激励广告展示，广告位ID: %s", finalPlacementId)
                // 记录当前显示的广告（用于资源释放）
                currentShowingAd = entry.ad
                entry.listener.awaitShow(activity, onRewardEarned)
            } else {
                totalShowFailCount++
                AdEventReporter.reportShowFail(AdType.REWARDED, AdPlatform.TOPON, finalPlacementId, totalShowFailCount, "No ad available", sessionId = currentSessionId, isPreload = currentIsPreload)
                AdResult.Failure(createAdException("TopOnrewarded ad no available ad"))
            }
        } catch (e: Exception) {
            AdLogger.e("TopOn激励广告展示异常", e)
            totalShowFailCount++
            AdEventReporter.reportShowFail(AdType.REWARDED, AdPlatform.TOPON, finalPlacementId, totalShowFailCount, e.message.orEmpty(), sessionId = currentSessionId, isPreload = currentIsPreload)
            AdResult.Failure(createAdException("TopOn rewarded: show exception: ${e.message}", e))
        }
    }

    /**
     * 加载广告
     */
    private suspend fun loadAd(context: Context, placementId: String): AdResult<TopOnRewardedAdEntry> {
        totalLoadCount++
        AdLogger.d("TopOn激励广告开始加载，广告位ID: %s，当前累计加载次数: %d", placementId, totalLoadCount)

        val requestId = AdEventReporter.reportStartLoad(AdType.REWARDED, AdPlatform.TOPON, placementId, totalLoadCount)

        return suspendCancellableCoroutine { continuation ->
            try {
                val applicationContext = context.applicationContext
                val rewardedVideoAd = TURewardVideoAd(applicationContext, placementId)
                val listener = TopOnRewardedVideoListener(
                    placementId = placementId,
                    startLoadTime = System.currentTimeMillis(),
                    rewardedVideoAd = rewardedVideoAd,
                    applicationContext = applicationContext,
                    requestId = requestId
                )
                listener.attachLoadContinuation(continuation)

                rewardedVideoAd.setAdListener(listener)
                rewardedVideoAd.setAdRevenueListener(listener)

                continuation.invokeOnCancellation {
                    listener.clearLoadContinuation()
                }

                rewardedVideoAd.load(applicationContext)
            } catch (e: Exception) {
                AdLogger.e("TopOn激励广告load exception", e)
                totalLoadFailCount++
                AdEventReporter.reportLoadFail(AdType.REWARDED, AdPlatform.TOPON, placementId, totalLoadFailCount, "", 0, e.message.orEmpty(), requestId)
                if (continuation.isActive) {
                    continuation.resume(AdResult.Failure(createAdException("TopOn rewarded ad loadAd exception: ${e.message}", e)))
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
        AdLogger.d("TopOn激励广告缓存已清理")
    }

    /**
     * 销毁控制器
     */
    fun destroy() {
        destroyAd()
        AdLogger.d("TopOn激励广告控制器已清理")
    }
    
    /**
     * 销毁正在显示的广告
     * 在Activity销毁或广告关闭时调用，释放正在显示的激励广告资源
     */
    fun destroyShowingAd() {
        currentShowingAd?.let { ad ->
            ad.setAdListener(null)
            ad.setAdRevenueListener(null)
            AdLogger.d("TopOn激励广告正在显示的广告已销毁")
        }
        currentShowingAd = null
    }

    /**
     * 获取激励广告是否正在展示
     */
    fun isAdShowing(): Boolean {
        return isShowing
    }

    /**
     * 获取当前缓存的广告对象（用于竞价）
     */
    fun getCurrentAd(placementId: String? = null): com.thinkup.rewardvideo.api.TURewardVideoAd? {
        val finalPlacementId = resolvePlacementId(placementId)
        if (finalPlacementId.isBlank()) {
            return null
        }
        
        return synchronized(adCache) {
            adCache[finalPlacementId]?.takeUnless { it.isExpired() }?.ad
        }
    }

    /**
     * TopOn 激励视频广告监听器
     */
    private inner class TopOnRewardedVideoListener(
        private val placementId: String,
        private val startLoadTime: Long,
        private val rewardedVideoAd: TURewardVideoAd,
        private val applicationContext: Context,
        private val requestId: String = ""
    ) : TURewardVideoListener, TUAdRevenueListener {

        private var loadContinuation: kotlinx.coroutines.CancellableContinuation<AdResult<TopOnRewardedAdEntry>>? = null
        private var showContinuation: kotlinx.coroutines.CancellableContinuation<AdResult<Unit>>? = null
        private var lastAdInfo: TUAdInfo? = null
        private var cacheTime: Long = System.currentTimeMillis()
        private var hasRewarded: Boolean = false
        private var rewardCallback: ((String, Int) -> Unit)? = null
        var currentAdUniqueId = ""

        fun attachLoadContinuation(continuation: kotlinx.coroutines.CancellableContinuation<AdResult<TopOnRewardedAdEntry>>) {
            loadContinuation = continuation
        }

        fun clearLoadContinuation() {
            loadContinuation = null
        }

        private fun resumeLoad(result: AdResult<TopOnRewardedAdEntry>) {
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

        suspend fun awaitShow(activity: FragmentActivity, onRewardEarned: ((String, Int) -> Unit)?): AdResult<Unit> {
            if (!rewardedVideoAd.isAdReady) {
                AdLogger.w("TopOn激励ad not ready，展示终止，广告位ID: %s", placementId)
                totalShowFailCount++
                AdEventReporter.reportShowFail(AdType.REWARDED, AdPlatform.TOPON, placementId, totalShowFailCount, "ad not ready", sessionId = currentSessionId, isPreload = currentIsPreload)
                return AdResult.Failure(createAdException("TopOnrewarded ad not ready, isAdReady=false"))
            }

            rewardCallback = onRewardEarned
            hasRewarded = false

            return suspendCancellableCoroutine { continuation ->
                showContinuation = continuation
                continuation.invokeOnCancellation {
                    showContinuation = null
                }

                try {
                    rewardedVideoAd.show(activity)
                } catch (e: Exception) {
                    AdLogger.e("TopOn激励广告调用show异常", e)
                    totalShowFailCount++
                    AdEventReporter.reportShowFail(AdType.REWARDED, AdPlatform.TOPON, placementId, totalShowFailCount, e.message.orEmpty(), sessionId = currentSessionId, isPreload = currentIsPreload)
                    if (continuation.isActive) {
                        continuation.resume(
                            AdResult.Failure(createAdException("TopOn rewarded: show callback failed: ${e.message}", e))
                        )
                    }
                    showContinuation = null
                }
            }
        }

        override fun onRewardedVideoAdLoaded() {
            val loadTime = System.currentTimeMillis() - startLoadTime
            totalLoadSucCount++
            cacheTime = System.currentTimeMillis()

            val adInfo = runCatching { rewardedVideoAd.checkValidAdCaches().firstOrNull() }.getOrNull()

            AdLogger.d(
                "TopOn激励广告加载成功，广告位ID: %s，耗时: %dms，缓存成功次数: %d",
                placementId,
                loadTime,
                totalLoadSucCount
            )

            AdEventReporter.reportLoaded(AdType.REWARDED, AdPlatform.TOPON, placementId, totalLoadSucCount, adInfo?.networkName.orEmpty(), ceil(loadTime / 1000.0).toInt(), requestId)

            val entry = TopOnRewardedAdEntry(
                placementId = placementId,
                ad = rewardedVideoAd,
                listener = this,
                cacheTime = cacheTime
            )

            synchronized(adCache) {
                adCache[placementId] = entry
            }

            resumeLoad(AdResult.Success(entry))
        }

        override fun onRewardedVideoAdFailed(adError: AdError) {
            val loadTime = System.currentTimeMillis() - startLoadTime
            val errorMsg = adError.desc ?: adError.getFullErrorInfo()
            AdLogger.e(
                "TopOnrewarded ad load failed，广告位ID: %s，耗时: %dms，错误: %s",
                placementId,
                loadTime,
                adError.getFullErrorInfo()
            )

            totalLoadFailCount++
            AdEventReporter.reportLoadFail(AdType.REWARDED, AdPlatform.TOPON, placementId, totalLoadFailCount, "", ceil(loadTime / 1000.0).toInt(), errorMsg, requestId)

            resumeLoad(AdResult.Failure(AdException(adError.code.toInt(), errorMsg)))
        }

        override fun onRewardedVideoAdPlayStart(adInfo: TUAdInfo) {
            AdLogger.d("TopOn激励广告开始播放")
            isShowing = true
            lastAdInfo = adInfo
            AdConfigManager.recordShow(AdType.REWARDED, AdPlatform.TOPON)
        }

        override fun onRewardedVideoAdPlayEnd(adInfo: TUAdInfo) {
            AdLogger.d("TopOn激励广告播放结束")
            lastAdInfo = adInfo
        }

        override fun onRewardedVideoAdPlayFailed(adError: AdError, adInfo: TUAdInfo?) {
            AdLogger.w("TopOn激励广告play failed: %s", adError.desc ?: adError.getFullErrorInfo())
            isShowing = false
            totalShowFailCount++
            lastAdInfo = adInfo

            AdEventReporter.reportShowFail(AdType.REWARDED, AdPlatform.TOPON, placementId, totalShowFailCount, adError.desc ?: adError.getFullErrorInfo(), adInfo?.networkName ?: "", sessionId = currentSessionId, isPreload = currentIsPreload)

            synchronized(adCache) {
                adCache.remove(placementId)
            }

            // 展示失败后重新预缓存
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    AdLogger.d("TopOn激励广告play failed，开始重新预缓存，广告位ID: %s", placementId)
                    preloadAd(applicationContext, placementId)
                } catch (e: Exception) {
                    AdLogger.e("TopOn激励广告重新预缓存失败", e)
                }
            }

            resumeShow(AdResult.Failure(createAdException("TopOn rewarded: play failed: ${adError.desc ?: "unknown"}")))
        }

        override fun onRewardedVideoAdClosed(adInfo: TUAdInfo) {
            AdLogger.d("TopOn激励广告关闭")
            isShowing = false
            totalCloseCount++
            lastAdInfo = adInfo

            AdEventReporter.builder(com.android.common.bill.ads.tracker.AdEventType.CLOSE)
                .adType(AdType.REWARDED)
                .platform(AdPlatform.TOPON)
                .adUnitId(placementId)
                .number(totalCloseCount)
                .adSource(adInfo.networkName ?: "")
                .value(adInfo.publisherRevenue ?: 0.0)
                .currency(adInfo.currency ?: "")
                .param("isended", if (hasRewarded) "true" else "")
                .report()

            synchronized(adCache) {
                adCache.remove(placementId)
            }

            // 激励关闭时重新预缓存
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    AdLogger.d("TopOn激励广告关闭，开始重新预缓存，广告位ID: %s", placementId)
                    preloadAd(applicationContext, placementId)
                } catch (e: Exception) {
                    AdLogger.e("TopOn激励广告重新预缓存失败", e)
                }
            }

            resumeShow(AdResult.Success(Unit))
        }

        override fun onRewardedVideoAdPlayClicked(adInfo: TUAdInfo) {
            AdLogger.d("TopOn激励广告被点击")
            totalClickCount++
            lastAdInfo = adInfo
            AdLogger.d("TopOn激励广告累积点击次数: $totalClickCount")

            AdClickProtectionController.recordClick(currentAdUniqueId)
            AdConfigManager.recordClick(AdType.REWARDED, AdPlatform.TOPON)

            AdEventReporter.reportClick(AdType.REWARDED, AdPlatform.TOPON, placementId, currentAdUniqueId, totalClickCount, adInfo.networkName ?: "", adInfo.publisherRevenue ?: 0.0, adInfo.currency ?: "")
        }

        override fun onReward(adInfo: TUAdInfo) {
            AdLogger.d("TopOn用户获得奖励")
            hasRewarded = true
            totalRewardEarnedCount++
            lastAdInfo = adInfo
            AdLogger.d("TopOn激励广告累积奖励获得次数: $totalRewardEarnedCount")

            val rewardType = adInfo.scenarioRewardName ?: "default"
            val rewardAmount = adInfo.scenarioRewardNumber

            AdEventReporter.reportRewardEarned(AdType.REWARDED, AdPlatform.TOPON, placementId, totalRewardEarnedCount, adInfo.networkName ?: "", rewardType, rewardAmount)

            rewardCallback?.invoke(rewardType, rewardAmount)
        }

        override fun onAdRevenuePaid(adInfo: TUAdInfo) {
            lastAdInfo = adInfo
            totalShowCount++
            AdLogger.d(
                "TopOn激励广告收益回调，value=${adInfo.publisherRevenue ?: adInfo.ecpm}, currency=${adInfo.currency}"
            )

            currentAdUniqueId = "${java.util.UUID.randomUUID()}_${adInfo.placementId}_${adInfo.adsourceId}"

            AdEventReporter.reportImpression(AdType.REWARDED, AdPlatform.TOPON, placementId, currentAdUniqueId, totalShowCount, adInfo.networkName ?: "", adInfo.publisherRevenue ?: 0.0, adInfo.currency ?: "", sessionId = currentSessionId, isPreload = currentIsPreload)

            val revenueValue = adInfo.publisherRevenue ?: 0.0
            // TopOn 的 revenueValue 已经是美元，不需要转换
            val revenueUsd = revenueValue.toLong()

            AdRevenueReporter.reportRevenue(
                AdType.REWARDED,
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

        return BillConfig.topon.rewardedId
    }
}
