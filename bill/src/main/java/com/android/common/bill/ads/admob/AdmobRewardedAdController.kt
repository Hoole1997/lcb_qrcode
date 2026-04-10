package com.android.common.bill.ads.admob

import android.content.Context
import androidx.fragment.app.FragmentActivity
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdValue
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.OnPaidEventListener
import com.google.android.gms.ads.OnUserEarnedRewardListener
import com.google.android.gms.ads.rewarded.RewardItem
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.android.common.bill.BillConfig
import com.android.common.bill.ads.AdException
import com.android.common.bill.ads.AdResult
import com.android.common.bill.ads.config.AdConfigManager
import com.android.common.bill.ads.config.AdPlatform
import com.android.common.bill.ads.config.AdType
import com.android.common.bill.ads.protection.AdClickProtectionController
import com.android.common.bill.ads.tracker.AdRevenueReporter
import net.corekit.core.ext.DataStoreIntDelegate
import net.corekit.core.report.ReportDataManager
import com.android.common.bill.ads.log.AdLogger
import com.android.common.bill.ads.tracker.AdEventReporter
import com.android.common.bill.ads.util.AdDestroyManager
import com.android.common.bill.ads.util.AdLifecycleGuard
import com.android.common.bill.ads.util.PositionGet
import com.android.common.bill.ads.util.runAdmobLoadOnMainThread
import com.android.common.bill.ui.dialog.ADLoadingDialog
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.also
import kotlin.collections.count
import kotlin.collections.firstOrNull
import kotlin.collections.indexOfFirst
import kotlin.coroutines.resume
import kotlin.let
import kotlin.text.orEmpty
import kotlin.to

/**
 * 激励广告控制器
 */
class AdmobRewardedAdController private constructor() {

    // 累积点击统计（持久化）
    private var totalClickCount by DataStoreIntDelegate("rewarded_ad_total_clicks", 0)

    // 累积关闭统计（持久化）
    private var totalCloseCount by DataStoreIntDelegate("rewarded_ad_total_close", 0)

    // 累积加载次数统计（持久化）
    private var totalLoadCount by DataStoreIntDelegate("rewarded_ad_total_loads", 0)

    // 累积加载成功次数统计（持久化）
    private var totalLoadSucCount by DataStoreIntDelegate("rewarded_ad_total_load_suc", 0)

    // 累积加载失败次数统计（持久化）
    private var totalLoadFailCount by DataStoreIntDelegate("rewarded_ad_total_load_fails", 0)

    // 累积展示失败次数统计（持久化）
    private var totalShowFailCount by DataStoreIntDelegate("rewarded_ad_total_show_fails", 0)

    // 累积触发统计（持久化）
    private var totalShowTriggerCount by DataStoreIntDelegate("rewarded_ad_total_show_triggers", 0)

    // 累积展示统计（持久化）
    private var totalShowCount by DataStoreIntDelegate("rewarded_ad_total_shows", 0)

    // 累积奖励获得次数统计（持久化）
    private var totalRewardEarnedCount by DataStoreIntDelegate("rewarded_ad_total_reward_earned", 0)

    // 当前广告的收益信息（临时存储）
    private var currentAdValue: AdValue? = null

    // 激励广告是否正在显示的标识
    private var isShowing: Boolean = false
    
    // 当前正在显示的广告（用于资源释放）
    private var currentShowingAd: RewardedAd? = null
    
    // 展示结果回调
    private var showContinuation: kotlinx.coroutines.CancellableContinuation<AdResult<Unit>>? = null
    
    // 奖励回调
    private var rewardCallback: ((RewardItem) -> Unit)? = null

    companion object {
        private const val DEFAULT_CACHE_SIZE_PER_AD_UNIT = 1

        @Volatile
        private var INSTANCE: AdmobRewardedAdController? = null

        fun getInstance(): AdmobRewardedAdController {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AdmobRewardedAdController().also { INSTANCE = it }
            }
        }
    }

    // 内存缓存池 - 存储预加载的广告
    private val adCachePool = mutableListOf<CachedRewardedAd>()
    private val maxCacheSizePerAdUnit = DEFAULT_CACHE_SIZE_PER_AD_UNIT


    /**
     * 缓存的激励广告数据类
     */
    private data class CachedRewardedAd(
        val ad: RewardedAd,
        val adUnitId: String,
        val loadTime: Long = System.currentTimeMillis()
    ) {
        fun isExpired(): Boolean {
            return System.currentTimeMillis() - loadTime > 1 * 60 * 60 * 1000L
        }
    }

    /**
     * 预加载广告
     */
    suspend fun preloadAd(context: Context, adUnitId: String? = null): AdResult<Unit> {
        val finalAdUnitId = adUnitId ?: BillConfig.admob.rewardedId
        return loadAdToCache(context, finalAdUnitId)
    }

    /**
     * 显示广告
     */
    suspend fun showAd(activity: FragmentActivity, adUnitId: String? = null, onRewardEarned: ((RewardItem) -> Unit)? = null, sessionId: String = ""): AdResult<Unit> {
        val finalAdUnitId = adUnitId ?: BillConfig.admob.rewardedId

        // 累积触发统计
        totalShowTriggerCount++
        AdLogger.d("Admob激励广告累积触发展示次数: $totalShowTriggerCount")
        
        // 生命周期检查：等待 Activity Resume 或取消
        val lifecycleGuard = AdLifecycleGuard.instance
        when (val lifecycleResult = lifecycleGuard.awaitResumeOrCancel(activity)) {
            is AdResult.Failure -> {
                AdLogger.w("Admob激励广告展示被取消：Activity生命周期不满足")
                totalShowFailCount++
                AdEventReporter.reportShowFail(
                    AdType.REWARDED,
                    AdPlatform.ADMOB,
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
            AdLogger.d("Admob激励广告: Activity销毁，清理展示资源")
            // 销毁正在显示的广告对象
            destroyShowingAd()
            showContinuation?.let {
                if (it.isActive) {
                    totalShowFailCount++
                    AdEventReporter.reportShowFail(AdType.REWARDED, AdPlatform.ADMOB, finalAdUnitId, totalShowFailCount, "Activity destroyed", sessionId = sessionId, isPreload = false)
                    it.resume(AdResult.Failure(createAdException("Admob rewarded: Activity destroyed")))
                }
            }
            showContinuation = null
            rewardCallback = null
            isShowing = false
        }

        // 检查缓存过期
        synchronized(adCachePool) {
            if (adCachePool.any { it.adUnitId == finalAdUnitId && it.isExpired() }) {
                AdEventReporter.reportTimeoutCache(AdType.REWARDED, AdPlatform.ADMOB, finalAdUnitId)
            }
        }

        return try {
            // 1. 尝试从缓存获取广告
            var cachedAd = getCachedAd(finalAdUnitId)
            var isPreload = cachedAd != null

            // 2. 如果缓存为空，立即加载并缓存一个广告
            if (cachedAd == null) {
                AdLogger.d("Admob缓存为空，立即加载激励广告，广告位ID: %s", finalAdUnitId)
                loadAdToCache(activity, finalAdUnitId)
                cachedAd = getCachedAd(finalAdUnitId)
                isPreload = false
            }

            if (cachedAd != null) {
                AdLogger.d("Admob使用缓存中的激励广告，广告位ID: %s", finalAdUnitId)

                // 3. 显示广告
                val result = showAdInternal(activity, cachedAd.ad, finalAdUnitId, sessionId = sessionId, isPreload = isPreload, onRewardEarned = onRewardEarned)

                result
            } else {
                totalShowFailCount++
                AdEventReporter.reportShowFail(AdType.REWARDED, AdPlatform.ADMOB, finalAdUnitId, totalShowFailCount, "No ad available", sessionId = sessionId, isPreload = false)
                AdResult.Failure(createAdException("Admob rewarded ad no available ad"))
            }
        } catch (e: Exception) {
            AdLogger.e("Admob显示激励广告异常", e)
            totalShowFailCount++
            AdEventReporter.reportShowFail(AdType.REWARDED, AdPlatform.ADMOB, finalAdUnitId, totalShowFailCount, e.message.orEmpty(), sessionId = sessionId, isPreload = false)
            AdResult.Failure(createAdException("Admob rewarded: show exception: ${e.message}", e))
        }
    }

    /**
     * 基础广告加载方法（可复用）
     */
    private suspend fun loadAd(context: Context, adUnitId: String): RewardedAd? {
        // 累积加载次数统计
        totalLoadCount++
        AdLogger.d("Admob激励广告累积加载次数: $totalLoadCount")

        val requestId = AdEventReporter.reportStartLoad(AdType.REWARDED, AdPlatform.ADMOB, adUnitId, totalLoadCount)

        return runAdmobLoadOnMainThread {
            suspendCancellableCoroutine { continuation ->
                val startTime = System.currentTimeMillis()

                val adRequest = AdRequest.Builder().build()

                RewardedAd.load(context, adUnitId, adRequest, object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(rewardedAd: RewardedAd) {
                        val loadTime = System.currentTimeMillis() - startTime
                        AdLogger.d("Admob激励广告加载成功，广告位ID: %s, 耗时: %dms", adUnitId, loadTime)
                        totalLoadSucCount++
                        AdEventReporter.reportLoaded(AdType.REWARDED, AdPlatform.ADMOB, adUnitId, totalLoadSucCount, rewardedAd.responseInfo?.loadedAdapterResponseInfo?.adSourceName.orEmpty(), ceil(loadTime / 1000.0).toInt(), requestId)

                        continuation.resume(rewardedAd)
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        val loadTime = System.currentTimeMillis() - startTime
                        AdLogger.e(
                            "Admob rewarded ad load failed，广告位ID: %s, 耗时: %dms, 错误: %s",
                            adUnitId,
                            loadTime,
                            loadAdError.message
                        )

                        totalLoadFailCount++
                        AdEventReporter.reportLoadFail(AdType.REWARDED, AdPlatform.ADMOB, adUnitId, totalLoadFailCount, loadAdError.responseInfo?.loadedAdapterResponseInfo?.adSourceName.orEmpty(), ceil(loadTime / 1000.0).toInt(), loadAdError.message, requestId)

                        continuation.resume(null)
                    }
                })
            }
        }
    }

    /**
     * 加载广告到缓存
     */
    suspend fun loadAdToCache(context: Context, adUnitId: String): AdResult<Unit> {
        return try {

            // 检查缓存是否已满
            val currentAdUnitCount = getCachedAdCount(adUnitId)
            if (currentAdUnitCount >= maxCacheSizePerAdUnit) {
                AdLogger.w("Admob广告位 %s 缓存已满，当前缓存: %d/%d", adUnitId, currentAdUnitCount, maxCacheSizePerAdUnit)
                return AdResult.Success(Unit)
            }

            // 加载广告
            val rewardedAd = loadAd(context, adUnitId)
            if (rewardedAd != null) {
                synchronized(adCachePool) {
                    adCachePool.add(CachedRewardedAd(rewardedAd, adUnitId))
                    val currentCount = getCachedAdCount(adUnitId)
                    AdLogger.d(
                        "Admob激励广告加载成功并缓存，广告位ID: %s，该广告位缓存数量: %d/%d",
                        adUnitId,
                        currentCount,
                        maxCacheSizePerAdUnit
                    )
                }
                AdResult.Success(Unit)
            } else {
                AdResult.Failure(createAdException("Admob rewarded ad load returned null"))
            }
        } catch (e: Exception) {
            AdLogger.e("Admob激励loadAdToCache异常", e)
            AdResult.Failure(AdException(0, "Admob rewarded ad loadAdToCache exception: ${e.message}", e))
        }
    }

    /**
     * 从缓存获取广告
     */
    private fun getCachedAd(adUnitId: String): CachedRewardedAd? {
        synchronized(adCachePool) {
            val index = adCachePool.indexOfFirst { it.adUnitId == adUnitId && !it.isExpired() }
            return if (index != -1) {
                adCachePool.removeAt(index)
            } else {
                null
            }
        }
    }

    fun peekCachedAd(adUnitId: String = BillConfig.admob.rewardedId): RewardedAd? {
        return synchronized(adCachePool) {
            adCachePool.firstOrNull { it.adUnitId == adUnitId && !it.isExpired() }?.ad
        }
    }

    /**
     * 获取指定广告位的缓存数量
     */
    private fun getCachedAdCount(adUnitId: String): Int {
        synchronized(adCachePool) {
            return adCachePool.count { it.adUnitId == adUnitId && !it.isExpired() }
        }
    }

    /**
     * 检查指定广告位缓存是否已满
     */
    private fun isCacheFull(adUnitId: String): Boolean {
        return getCachedAdCount(adUnitId) >= maxCacheSizePerAdUnit
    }

    /**
     * 显示广告的内部实现
     */
    private suspend fun showAdInternal(
        activity: FragmentActivity,
        rewardedAd: RewardedAd,
        adUnitId: String,
        sessionId: String = "",
        isPreload: Boolean = false,
        onRewardEarned: ((RewardItem) -> Unit)?
    ): AdResult<Unit> {
        // 记录当前显示的广告（用于资源释放）
        currentShowingAd = rewardedAd
        
        return suspendCancellableCoroutine { continuation ->
            var hasRewarded = false
            var currentAdUniqueId = ""

            rewardedAd.onPaidEventListener = OnPaidEventListener { adValue ->
                AdLogger.d("Admob激励广告收益回调: value=${adValue.valueMicros}, currency=${adValue.currencyCode}")

                val uuid = java.util.UUID.randomUUID().toString()
                val creativeId = rewardedAd.responseInfo?.loadedAdapterResponseInfo?.adSourceInstanceId.orEmpty()
                currentAdUniqueId = "${uuid}_${adUnitId}_${creativeId}"

                currentAdValue = adValue

                AdEventReporter.reportImpression(AdType.REWARDED, AdPlatform.ADMOB, adUnitId, currentAdUniqueId, totalShowCount, rewardedAd.responseInfo?.loadedAdapterResponseInfo?.adSourceName.orEmpty(), currentAdValue?.let { it.valueMicros / 1_000_000.0 } ?: 0.0, currentAdValue?.currencyCode ?: "", sessionId = sessionId, isPreload = isPreload)

                AdRevenueReporter.reportRevenue(AdType.REWARDED, AdPlatform.ADMOB, adUnitId, adValue.valueMicros / 1_000_000.0, adValue.currencyCode, rewardedAd.responseInfo?.loadedAdapterResponseInfo?.adSourceName ?: "Admob", rewardedAd.responseInfo?.loadedAdapterResponseInfo?.adSourceInstanceName.orEmpty())
            }

            rewardedAd.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    AdLogger.d("Admob激励广告关闭")

                    // 设置广告不再显示标识
                    isShowing = false

                    totalCloseCount++

                    AdEventReporter.builder(com.android.common.bill.ads.tracker.AdEventType.CLOSE)
                        .adType(AdType.REWARDED)
                        .platform(AdPlatform.ADMOB)
                        .adUnitId(adUnitId)
                        .number(totalCloseCount)
                        .adSource(rewardedAd.responseInfo?.loadedAdapterResponseInfo?.adSourceName.orEmpty())
                        .value(currentAdValue?.let { it.valueMicros / 1_000_000.0 } ?: 0.0)
                        .currency(currentAdValue?.currencyCode ?: "")
                        .param("isended", if (hasRewarded) "true" else "")
                        .report()

                    // 销毁广告对象，避免内存泄露
                    rewardedAd.onPaidEventListener = null
                    rewardedAd.fullScreenContentCallback = null

                    // 异步预加载下一个广告到缓存（如果缓存未满）
                    if (!isCacheFull(adUnitId)) {
                        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                            try {
                                AdLogger.d("Admob激励广告关闭，开始重新预缓存，广告位ID: %s", adUnitId)
                                preloadAd(activity.applicationContext, adUnitId)
                            } catch (e: Exception) {
                                AdLogger.e("Admob激励广告重新预缓存失败", e)
                            }
                        }
                    }

                    val result = AdResult.Success(Unit)
                    if (continuation.isActive) {
                        continuation.resume(result)
                    }
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    AdLogger.w("Admob激励广告show failed: %s", adError.message)

                    // 累积展示失败次数统计
                    totalShowFailCount++
                    AdLogger.d("Admob激励广告累积展示失败次数: $totalShowFailCount")

                    AdEventReporter.reportShowFail(AdType.REWARDED, AdPlatform.ADMOB, adUnitId, totalShowFailCount, adError.message, sessionId = sessionId, isPreload = isPreload)

                    // 销毁广告对象，避免内存泄露
                    rewardedAd.onPaidEventListener = null
                    rewardedAd.fullScreenContentCallback = null

                    // 异步预加载下一个广告到缓存（如果缓存未满）
                    if (!isCacheFull(adUnitId)) {
                        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                            try {
                                AdLogger.d("Admob激励广告展示失败，开始重新预缓存，广告位ID: %s", adUnitId)
                                preloadAd(activity.applicationContext, adUnitId)
                            } catch (e: Exception) {
                                AdLogger.e("Admob激励广告重新预缓存失败", e)
                            }
                        }
                    }

                    val result = AdResult.Failure(createAdException("Admob rewarded: show callback failed: ${adError.message}"))
                    if (continuation.isActive) {
                        continuation.resume(result)
                    }
                }

                override fun onAdShowedFullScreenContent() {
                    AdLogger.d("Admob激励广告开始显示")
                    AdConfigManager.recordShow(AdType.REWARDED, AdPlatform.ADMOB)
                }

                override fun onAdClicked() {
                    AdLogger.d("Admob激励广告被点击")

                    // 累积点击统计
                    totalClickCount++
                    AdLogger.d("Admob激励广告累积点击次数: $totalClickCount")

                    AdConfigManager.recordClick(AdType.REWARDED, AdPlatform.ADMOB)

                    // 记录点击用于重复点击保护
                    AdClickProtectionController.recordClick(currentAdUniqueId)

                    AdEventReporter.reportClick(AdType.REWARDED, AdPlatform.ADMOB, adUnitId, currentAdUniqueId, totalClickCount, rewardedAd.responseInfo?.loadedAdapterResponseInfo?.adSourceName.orEmpty(), currentAdValue?.let { it.valueMicros / 1_000_000.0 } ?: 0.0, currentAdValue?.currencyCode ?: "")
                }

                override fun onAdImpression() {
                    AdLogger.d("Admob激励广告展示完成")

                    // 设置广告正在显示标识
                    isShowing = true

                    // 累积展示统计
                    totalShowCount++
                    AdLogger.d("Admob激励广告累积展示次数: $totalShowCount")
                }
            }

            rewardedAd.show(activity, OnUserEarnedRewardListener { rewardItem ->
                AdLogger.d("用户获得奖励: type=${rewardItem.type}, amount=${rewardItem.amount}")

                // 累积奖励获得次数统计
                totalRewardEarnedCount++
                AdLogger.d("激励广告累积奖励获得次数: $totalRewardEarnedCount")
                hasRewarded = true

                AdEventReporter.reportRewardEarned(AdType.REWARDED, AdPlatform.ADMOB, adUnitId, totalRewardEarnedCount, rewardedAd.responseInfo?.loadedAdapterResponseInfo?.adSourceName.orEmpty(), rewardItem.type, rewardItem.amount)

                // 调用外部回调
                onRewardEarned?.invoke(rewardItem)
            })
        }
    }

    /**
     * 销毁广告
     */
    fun destroyAd() {
        synchronized(adCachePool) {
            adCachePool.clear()
        }
        AdLogger.d("激励广告已销毁")
    }
    
    /**
     * 销毁正在显示的广告
     * 在Activity销毁或广告关闭时调用，释放正在显示的激励广告资源
     */
    fun destroyShowingAd() {
        currentShowingAd?.let { ad ->
            ad.onPaidEventListener = null
            ad.fullScreenContentCallback = null
            AdLogger.d("Admob激励广告正在显示的广告已销毁")
        }
        currentShowingAd = null
    }


    /**
     * 销毁控制器
     */
    fun destroy() {
        destroyAd()
        AdLogger.d("激励广告控制器已清理")
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
     * 获取激励广告是否正在显示的状态
     * @return true 如果激励广告正在显示，false 否则
     */
    fun isAdShowing(): Boolean {
        return isShowing
    }
}
