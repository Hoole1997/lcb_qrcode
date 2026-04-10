package com.android.common.bill.ads.pangle

import android.content.Context
import androidx.fragment.app.FragmentActivity
import com.bytedance.sdk.openadsdk.api.model.PAGAdEcpmInfo
import com.bytedance.sdk.openadsdk.api.model.PAGErrorModel
import com.bytedance.sdk.openadsdk.api.model.PAGRevenueInfo
import com.bytedance.sdk.openadsdk.api.reward.PAGRewardItem
import com.bytedance.sdk.openadsdk.api.reward.PAGRewardedAd
import com.bytedance.sdk.openadsdk.api.reward.PAGRewardedAdInteractionCallback
import com.bytedance.sdk.openadsdk.api.reward.PAGRewardedAdLoadCallback
import com.bytedance.sdk.openadsdk.api.reward.PAGRewardedRequest
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
import com.android.common.bill.ads.util.AdDestroyManager
import com.android.common.bill.ads.util.AdLifecycleGuard
import net.corekit.core.ads.RevenueAdData
import net.corekit.core.ads.RevenueAdManager
import net.corekit.core.ads.RevenueInfo
import com.android.common.bill.ads.util.PositionGet
import com.android.common.bill.ui.dialog.ADLoadingDialog
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
 * Pangle激励视频广告控制器
 * 参考文档：https://www.pangleglobal.com/integration/android-rewarded-video-ads
 */
class PangleRewardedAdController private constructor() {

    // 当前展示的sessionId和isPreload
    private var currentSessionId: String = ""
    private var currentIsPreload: Boolean = false

    private var totalClickCount by DataStoreIntDelegate("pangle_rewarded_ad_total_clicks", 0)
    private var totalCloseCount by DataStoreIntDelegate("pangle_rewarded_ad_total_close", 0)
    private var totalLoadCount by DataStoreIntDelegate("pangle_rewarded_ad_total_loads", 0)
    private var totalLoadSucCount by DataStoreIntDelegate("pangle_rewarded_ad_total_load_suc", 0)
    private var totalLoadFailCount by DataStoreIntDelegate("pangle_rewarded_ad_total_load_fails", 0)
    private var totalShowFailCount by DataStoreIntDelegate("pangle_rewarded_ad_total_show_fails", 0)
    private var totalShowTriggerCount by DataStoreIntDelegate("pangle_rewarded_ad_total_show_triggers", 0)
    private var totalShowCount by DataStoreIntDelegate("pangle_rewarded_ad_total_shows", 0)
    private var totalRewardEarnedCount by DataStoreIntDelegate("pangle_rewarded_ad_total_reward_earned", 0)
    
    // 激励广告是否正在显示的标识
    private var isShowing: Boolean = false
    
    // 当前正在显示的广告（用于资源释放）
    private var currentShowingAd: PAGRewardedAd? = null
    
    // 展示结果回调
    private var showContinuation: kotlinx.coroutines.CancellableContinuation<AdResult<Unit>>? = null
    
    // 奖励回调
    private var rewardCallback: ((PAGRewardItem) -> Unit)? = null

    companion object {
        private const val TAG = "PangleRewardedAd"
        private const val CACHE_EXPIRE_MS = 60 * 60 * 1000L // 1小时过期

        @Volatile
        private var INSTANCE: PangleRewardedAdController? = null

        fun getInstance(): PangleRewardedAdController {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PangleRewardedAdController().also { INSTANCE = it }
            }
        }
    }

    /**
     * 缓存的广告实体
     */
    private data class CachedAdEntry(
        val adUnitId: String,
        val ad: PAGRewardedAd,
        val cacheTime: Long = System.currentTimeMillis()
    ) {
        fun isExpired(): Boolean {
            return System.currentTimeMillis() - cacheTime > CACHE_EXPIRE_MS || !ad.isReady
        }
    }

    private var cachedAdEntry: CachedAdEntry? = null

    suspend fun preloadAd(context: Context, adUnitId: String? = null): AdResult<Unit> {
        val finalAdUnitId = adUnitId ?: BillConfig.pangle.rewardedId
        
        // 检查当前缓存是否存在且未过期
        val cached = cachedAdEntry
        if (cached != null && !cached.isExpired() && cached.adUnitId == finalAdUnitId) {
            AdLogger.d("Pangle激励广告已有有效缓存且未过期，广告位ID: %s，跳过加载", finalAdUnitId)
            return AdResult.Success(Unit)
        }
        
        return loadAd(context, finalAdUnitId)
    }

    suspend fun showAd(
        activity: FragmentActivity,
        adUnitId: String? = null,
        onRewardEarned: ((PAGRewardItem) -> Unit)? = null,
        sessionId: String = ""
    ): AdResult<Unit> {
        val finalAdUnitId = adUnitId ?: BillConfig.pangle.rewardedId

        totalShowTriggerCount++
        AdLogger.d("Pangle激励广告累积触发展示次数: $totalShowTriggerCount")
        
        // 生命周期检查：等待 Activity Resume 或取消
        val lifecycleGuard = AdLifecycleGuard.instance
        when (val lifecycleResult = lifecycleGuard.awaitResumeOrCancel(activity)) {
            is AdResult.Failure -> {
                AdLogger.w("Pangle激励广告展示被取消：Activity生命周期不满足")
                totalShowFailCount++
                AdEventReporter.reportShowFail(
                    AdType.REWARDED,
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
            AdLogger.d("Pangle激励广告: Activity销毁，清理展示资源")
            // 销毁正在显示的广告对象
            destroyShowingAd()
            showContinuation?.let {
                if (it.isActive) {
                    totalShowFailCount++
                    AdEventReporter.reportShowFail(AdType.REWARDED, AdPlatform.PANGLE, finalAdUnitId, totalShowFailCount, "Activity destroyed", sessionId = sessionId, isPreload = false)
                    it.resume(AdResult.Failure(createAdException("Pangle rewarded: Activity destroyed")))
                }
            }
            showContinuation = null
            rewardCallback = null
            isShowing = false
        }
        

        currentSessionId = sessionId
        currentIsPreload = cachedAdEntry?.let { !it.isExpired() && it.adUnitId == finalAdUnitId } ?: false

        // 检查缓存过期
        if (cachedAdEntry?.isExpired() == true) {
            AdEventReporter.reportTimeoutCache(AdType.REWARDED, AdPlatform.PANGLE, finalAdUnitId)
        }

        return try {
            // 检查缓存是否存在且未过期
            val cached = cachedAdEntry
            if (cached == null || cached.isExpired() || cached.adUnitId != finalAdUnitId) {
                val loadResult = loadAd(activity, finalAdUnitId)
                if (loadResult is AdResult.Failure) {
                    return loadResult
                }
            }

            val entry = cachedAdEntry
            if (entry != null && !entry.isExpired()) {
                cachedAdEntry = null
                showAdInternal(activity, entry.ad, finalAdUnitId, onRewardEarned, sessionId = currentSessionId, isPreload = currentIsPreload)
            } else {
                totalShowFailCount++
                AdEventReporter.reportShowFail(AdType.REWARDED, AdPlatform.PANGLE, finalAdUnitId, totalShowFailCount, "load_failed", sessionId = currentSessionId, isPreload = currentIsPreload)
                AdResult.Failure(createAdException("Pangle rewarded ad no available ad"))
            }
        } catch (e: Exception) {
            totalShowFailCount++
            AdEventReporter.reportShowFail(AdType.REWARDED, AdPlatform.PANGLE, finalAdUnitId, totalShowFailCount, e.message.orEmpty(), sessionId = currentSessionId, isPreload = currentIsPreload)
            AdLogger.e("显示Pangle激励广告异常", e)
            AdResult.Failure(createAdException("Pangle rewarded: show exception: ${e.message}", e))
        }
    }

    suspend fun loadAd(context: Context, adUnitId: String): AdResult<Unit> {
        if (adUnitId.isBlank()) {
            return AdResult.Failure(createAdException("Pangle rewarded: ad unit ID is empty"))
        }

        totalLoadCount++
        AdLogger.d("Pangle激励广告开始加载，广告位ID: $adUnitId")
        val requestId = AdEventReporter.reportStartLoad(AdType.REWARDED, AdPlatform.PANGLE, adUnitId, totalLoadCount)

        return suspendCancellableCoroutine { continuation ->
            val startTime = System.currentTimeMillis()
            val request = PAGRewardedRequest(context)

            PAGRewardedAd.loadAd(adUnitId, request, object : PAGRewardedAdLoadCallback {
                override fun onAdLoaded(ad: PAGRewardedAd) {
                    val loadTime = System.currentTimeMillis() - startTime
                    AdLogger.d("Pangle激励广告加载成功，广告位ID: %s, 耗时: %dms", adUnitId, loadTime)
                    totalLoadSucCount++
                    AdEventReporter.reportLoaded(AdType.REWARDED, AdPlatform.PANGLE, adUnitId, totalLoadSucCount, AdPlatform.PANGLE.key, ceil(loadTime / 1000.0).toInt(), requestId)
                    cachedAdEntry = CachedAdEntry(adUnitId, ad)
                    continuation.resume(AdResult.Success(Unit))
                }

                override fun onError(model: PAGErrorModel) {
                    val code = model.errorCode
                    val message = model.errorMessage
                    val loadTime = System.currentTimeMillis() - startTime
                    AdLogger.e("Pangle rewarded ad load failed，广告位ID: %s, 耗时: %dms, 错误码: %d, 错误信息: %s", adUnitId, loadTime, code, message)
                    totalLoadFailCount++
                    AdEventReporter.reportLoadFail(AdType.REWARDED, AdPlatform.PANGLE, adUnitId, totalLoadFailCount, AdPlatform.PANGLE.key, ceil(loadTime / 1000.0).toInt(), message, requestId)
                    continuation.resume(AdResult.Failure(createAdException("Pangle rewarded ad load failed: $message")))
                }
            })
        }
    }

    fun getCurrentAd(adUnitId: String? = null): PAGRewardedAd? {
        val cached = cachedAdEntry ?: return null
        val targetUnitId = adUnitId ?: cached.adUnitId
        return if (cached.adUnitId == targetUnitId && !cached.isExpired()) cached.ad else null
    }

    private suspend fun showAdInternal(
        activity: FragmentActivity,
        rewardedAd: PAGRewardedAd,
        adUnitId: String,
        onRewardEarned: ((PAGRewardItem) -> Unit)?,
        sessionId: String = "",
        isPreload: Boolean = false
    ): AdResult<Unit> {
        // 记录当前显示的广告（用于资源释放）
        currentShowingAd = rewardedAd
        
        val applicationContext = activity.applicationContext
        // 保存奖励回调
        rewardCallback = onRewardEarned
        
        return suspendCancellableCoroutine { continuation ->
            // 保存 continuation 用于生命周期清理
            showContinuation = continuation
            
            continuation.invokeOnCancellation {
                showContinuation = null
                rewardCallback = null
            }
            
            var hasRewarded = false
            var currentRevenueUsd: Double? = null
            var currentCurrency: String? = null
            var currentAdSource: String? = null
            var currentPlacement: String? = null
            var currentRevenueAdUnit: String? = null
            var currentAdUniqueId = ""

            rewardedAd.setAdInteractionCallback(object : PAGRewardedAdInteractionCallback() {
                override fun onAdShowed() {
                    AdLogger.d("Pangle激励广告开始显示")
                    isShowing = true
                    val pagRevenueInfo: PAGRevenueInfo? = rewardedAd.pagRevenueInfo
                    val ecpmInfo: PAGAdEcpmInfo? = pagRevenueInfo?.showEcpm
                    currentCurrency = ecpmInfo?.currency
                    currentAdSource = ecpmInfo?.adnName
                    currentPlacement = ecpmInfo?.placement
                    currentRevenueAdUnit = ecpmInfo?.adUnit
                    // Pangle 的 revenue 本身就是美元，直接使用
                    val revenueUsd = ecpmInfo?.revenue?.toDoubleOrNull() ?: 0.0
                    currentRevenueUsd = revenueUsd
                    val impressionValue = revenueUsd
                    totalShowCount++

                    currentAdUniqueId = "${java.util.UUID.randomUUID()}_${ecpmInfo?.adUnit}_${ecpmInfo?.placement}"
                    AdConfigManager.recordShow(AdType.REWARDED, AdPlatform.PANGLE)
                    AdEventReporter.reportImpression(AdType.REWARDED, AdPlatform.PANGLE, adUnitId, currentAdUniqueId, totalShowCount, currentAdSource ?: AdPlatform.PANGLE.key, impressionValue, currentCurrency ?: "USD", sessionId = sessionId, isPreload = isPreload)

                    currentRevenueUsd?.let { revenueValue ->
                        AdRevenueReporter.reportRevenue(AdType.REWARDED, AdPlatform.PANGLE, currentRevenueAdUnit ?: adUnitId, revenueValue, currentCurrency ?: "USD", currentAdSource ?: AdPlatform.PANGLE.key, currentPlacement)
                        // Pangle 的 revenue 本身就是美元，直接使用
                        val revenueUsdLong = revenueValue.toLong()
                        AdLogger.d(
                            "Pangle激励广告收益上报(onShow): adUnit=%s, placement=%s, adn=%s, revenueUsd=%.4f, currency=%s",
                            currentRevenueAdUnit ?: adUnitId,
                            currentPlacement ?: "",
                            currentAdSource ?: AdPlatform.PANGLE.key,
                            revenueValue,
                            currentCurrency ?: ""
                        )
                    }
                }

                override fun onAdClicked() {
                    AdLogger.d("Pangle激励广告被点击")
                    totalClickCount++
                    AdConfigManager.recordClick(AdType.REWARDED, AdPlatform.PANGLE)

                    // 记录点击用于重复点击保护
                    AdClickProtectionController.recordClick(currentAdUniqueId)
                    AdEventReporter.reportClick(AdType.REWARDED, AdPlatform.PANGLE, adUnitId, currentAdUniqueId, totalClickCount, currentAdSource ?: AdPlatform.PANGLE.key, rewardedAd.pagRevenueInfo?.showEcpm?.revenue?.toDoubleOrNull() ?: 0.0, currentCurrency ?: "USD")
                }

                override fun onAdDismissed() {
                    AdLogger.d("Pangle激励广告关闭")
                    isShowing = false
                    closeEvent(
                        adUnitId = adUnitId,
                        adSource = currentAdSource,
                        valueUsd = currentRevenueUsd,
                        currencyCode = currentCurrency,
                        isEnded = if (hasRewarded) "true" else ""
                    )
                    
                    // 激励关闭时重新预缓存
                    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                        try {
                            AdLogger.d("Pangle激励广告关闭，开始重新预缓存，广告位ID: %s", adUnitId)
                            preloadAd(applicationContext, adUnitId)
                        } catch (e: Exception) {
                            AdLogger.e("Pangle激励广告重新预缓存失败", e)
                        }
                    }
                    
                    if (continuation.isActive) {
                        continuation.resume(AdResult.Success(Unit))
                    }
                }

                override fun onUserEarnedReward(rewardItem: PAGRewardItem) {
                    AdLogger.d("Pangle激励广告发放奖励: name=${rewardItem.rewardName}, amount=${rewardItem.rewardAmount}")
                    totalRewardEarnedCount++
                    hasRewarded = true
                    AdEventReporter.reportRewardEarned(AdType.REWARDED, AdPlatform.PANGLE, adUnitId, totalRewardEarnedCount, currentAdSource ?: AdPlatform.PANGLE.key, rewardItem.rewardName, rewardItem.rewardAmount)
                    onRewardEarned?.invoke(rewardItem)
                }

                override fun onUserEarnedRewardFail(model: PAGErrorModel) {
                    AdLogger.w("Pangle激励广告奖励下发失败，错误码: ${model.errorCode}")
                }

                override fun onAdShowFailed(model: PAGErrorModel) {
                    super.onAdShowFailed(model)
                    totalShowFailCount++
                    AdLogger.e(
                        "Pangle激励广告show failed: code=%d, message=%s",
                        model.errorCode,
                        model.errorMessage
                    )
                    AdEventReporter.reportShowFail(AdType.REWARDED, AdPlatform.PANGLE, adUnitId, totalShowFailCount, model.errorMessage.orEmpty(), currentAdSource ?: AdPlatform.PANGLE.key, sessionId = sessionId, isPreload = isPreload)

                    // 展示失败后重新预缓存
                    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                        try {
                            AdLogger.d("Pangle激励广告展示失败，开始重新预缓存，广告位ID: %s", adUnitId)
                            preloadAd(applicationContext, adUnitId)
                        } catch (e: Exception) {
                            AdLogger.e("Pangle激励广告重新预缓存失败", e)
                        }
                    }
                }
            })

            try {
                if (!rewardedAd.isReady) {
                    AdLogger.w("Pangle激励广告未就绪，无法显示")
                    isShowing = false
                    totalShowFailCount++
                    AdEventReporter.reportShowFail(AdType.REWARDED, AdPlatform.PANGLE, adUnitId, totalShowFailCount, "rewarded_not_ready", currentAdSource ?: AdPlatform.PANGLE.key, sessionId = sessionId, isPreload = isPreload)
                    if (continuation.isActive) {
                        continuation.resume(AdResult.Failure(createAdException("Pangle rewarded ad not ready, isAdReady=false")))
                    }
                    return@suspendCancellableCoroutine
                }

                rewardedAd.show(activity)
            } catch (e: Exception) {
                isShowing = false
                totalShowFailCount++
                AdEventReporter.reportShowFail(AdType.REWARDED, AdPlatform.PANGLE, adUnitId, totalShowFailCount, e.message.orEmpty(), currentAdSource ?: AdPlatform.PANGLE.key, sessionId = sessionId, isPreload = isPreload)
                if (continuation.isActive) {
                    continuation.resume(AdResult.Failure(createAdException("Pangle rewarded: show exception: ${e.message}", e)))
                }
            }
        }
    }

    fun hasCachedAd(): Boolean {
        val cached = cachedAdEntry
        return cached != null && !cached.isExpired()
    }

    fun destroy() {
        cachedAdEntry = null
        AdLogger.d("Pangle激励广告控制器已清理")
    }
    
    /**
     * 销毁正在显示的广告
     * 在Activity销毁或广告关闭时调用，释放正在显示的激励广告资源
     */
    fun destroyShowingAd() {
        currentShowingAd?.let { ad ->
            ad.setAdInteractionCallback(null)
            AdLogger.d("Pangle激励广告正在显示的广告已销毁")
        }
        currentShowingAd = null
    }

    private fun closeEvent(
        adUnitId: String,
        adSource: String? = "Pangle",
        valueUsd: Double? = null,
        currencyCode: String? = null,
        isEnded: String = ""
    ) {
        totalCloseCount++
        AdEventReporter.builder(com.android.common.bill.ads.tracker.AdEventType.CLOSE)
            .adType(AdType.REWARDED)
            .platform(AdPlatform.PANGLE)
            .adUnitId(adUnitId)
            .number(totalCloseCount)
            .adSource(adSource ?: AdPlatform.PANGLE.key)
            .value(valueUsd ?: 0.0)
            .currency(currencyCode ?: "USD")
            .param("isended", isEnded)
            .report()
    }

    private fun createAdException(message: String, cause: Throwable? = null): AdException {
        return AdException(
            code = -1,
            message = message,
            cause = cause
        )
    }

}
