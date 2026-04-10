package com.android.common.bill.ads.admob

import android.content.Context
import androidx.fragment.app.FragmentActivity
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdValue
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.OnPaidEventListener
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.android.common.bill.BillConfig
import com.android.common.bill.ads.AdException
import com.android.common.bill.ads.AdResult
import com.android.common.bill.ads.config.AdConfigManager
import com.android.common.bill.ads.config.AdPlatform
import com.android.common.bill.ads.config.AdType
import com.android.common.bill.ads.tracker.AdRevenueReporter
import net.corekit.core.ext.DataStoreIntDelegate
import net.corekit.core.report.ReportDataManager
import com.android.common.bill.ads.log.AdLogger
import com.android.common.bill.ads.protection.AdClickProtectionController
import com.android.common.bill.ads.tracker.AdEventReporter
import com.android.common.bill.ads.util.AdDestroyManager
import com.android.common.bill.ads.util.AdLifecycleGuard
import com.android.common.bill.ads.util.PositionGet
import com.android.common.bill.ads.util.runAdmobLoadOnMainThread
import kotlin.math.ceil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * 插页广告控制器
 */
class AdmobInterstitialAdController private constructor() {
    
    // 累积点击统计（持久化）
    private var totalClickCount by DataStoreIntDelegate("interstitial_ad_total_clicks", 0)

    // 累积关闭统计（持久化）
    private var totalCloseCount by DataStoreIntDelegate("interstitial_ad_total_close", 0)
    
    // 累积加载次数统计（持久化）
    private var totalLoadCount by DataStoreIntDelegate("interstitial_ad_total_loads", 0)

    // 累积加载成功次数统计（持久化）
    private var totalLoadSucCount by DataStoreIntDelegate("interstitial_ad_total_load_suc", 0)

    // 累积加载失败次数统计（持久化）
    private var totalLoadFailCount by DataStoreIntDelegate("interstitial_ad_total_load_fails", 0)
    
    // 累积展示失败次数统计（持久化）
    private var totalShowFailCount by DataStoreIntDelegate("interstitial_ad_total_show_fails", 0)
    
    // 累积触发统计（持久化）
    private var totalShowTriggerCount by DataStoreIntDelegate("interstitial_ad_total_show_triggers", 0)
    
    // 累积展示统计（持久化）
    private var totalShowCount by DataStoreIntDelegate("interstitial_ad_total_shows", 0)
    
    // 当前广告的收益信息（临时存储）
    private var currentAdValue: AdValue? = null
    
    // 插页广告是否正在显示的标识
    private var isShowing: Boolean = false
    
    // 当前正在显示的广告（用于资源释放）
    private var currentShowingAd: InterstitialAd? = null
    
    // 展示结果回调
    private var showContinuation: kotlinx.coroutines.CancellableContinuation<AdResult<Unit>>? = null
    
    companion object {
        private const val DEFAULT_CACHE_SIZE_PER_AD_UNIT = 1

        @Volatile
        private var INSTANCE: AdmobInterstitialAdController? = null

        fun getInstance(): AdmobInterstitialAdController {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AdmobInterstitialAdController().also { INSTANCE = it }
            }
        }
    }

    // 内存缓存池 - 存储预加载的广告
    private val adCachePool = mutableListOf<CachedInterstitialAd>()
    private val maxCacheSizePerAdUnit = DEFAULT_CACHE_SIZE_PER_AD_UNIT


    /**
     * 缓存的插页广告数据类
     */
    data class CachedInterstitialAd(
        val ad: InterstitialAd,
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
        val finalAdUnitId = adUnitId ?: BillConfig.admob.interstitialId
        return loadAdToCache(context, finalAdUnitId)
    }

    /**
     * 显示广告
     */
    suspend fun showAd(activity: FragmentActivity, adUnitId: String? = null, ignoreFullNative: Boolean = false, sessionId: String = ""): AdResult<Unit> {
        val finalAdUnitId = adUnitId ?: BillConfig.admob.interstitialId
        
        // 累积触发统计
        totalShowTriggerCount++
        AdLogger.d("Admob插页广告累积触发展示次数: $totalShowTriggerCount")
        
        // 生命周期检查：等待 Activity Resume 或取消
        val lifecycleGuard = AdLifecycleGuard.instance
        when (val lifecycleResult = lifecycleGuard.awaitResumeOrCancel(activity)) {
            is AdResult.Failure -> {
                AdLogger.w("Admob插页广告展示被取消：Activity生命周期不满足")
                totalShowFailCount++
                AdEventReporter.reportShowFail(
                    AdType.INTERSTITIAL,
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
            AdLogger.d("Admob插页广告: Activity销毁，清理展示资源")
            // 销毁正在显示的广告对象
            destroyShowingAd()
            showContinuation?.let {
                if (it.isActive) {
                    totalShowFailCount++
                    AdEventReporter.reportShowFail(AdType.INTERSTITIAL, AdPlatform.ADMOB, finalAdUnitId, totalShowFailCount, "Activity destroyed", sessionId = sessionId, isPreload = false)
                    it.resume(AdResult.Failure(createAdException("Admob interstitial: Activity destroyed")))
                }
            }
            showContinuation = null
            isShowing = false
        }

        // 检查缓存过期
        synchronized(adCachePool) {
            if (adCachePool.any { it.adUnitId == finalAdUnitId && it.isExpired() }) {
                AdEventReporter.reportTimeoutCache(AdType.INTERSTITIAL, AdPlatform.ADMOB, finalAdUnitId)
            }
        }

        return try {
            // 1. 尝试从缓存获取广告
            var cachedAd = getCachedAd(finalAdUnitId)
            var isPreload = cachedAd != null

            // 2. 如果缓存为空，立即加载并缓存一个广告
            if (cachedAd == null) {
                AdLogger.d("Admob缓存为空，立即加载插页广告，广告位ID: %s", finalAdUnitId)
                loadAdToCache(activity, finalAdUnitId)
                cachedAd = getCachedAd(finalAdUnitId)
                isPreload = false
            }

            if (cachedAd != null) {
                AdLogger.d("Admob使用缓存中的插页广告，广告位ID: %s", finalAdUnitId)

                // 3. 显示广告
                val result = showAdInternal(activity, cachedAd.ad, finalAdUnitId, sessionId = sessionId, isPreload = isPreload)

                result
            } else {
                totalShowFailCount++
                AdEventReporter.reportShowFail(AdType.INTERSTITIAL, AdPlatform.ADMOB, finalAdUnitId, totalShowFailCount, "No ad available", sessionId = sessionId, isPreload = false)
                AdResult.Failure(createAdException("Admob interstitial ad no available ad"))
            }
        } catch (e: Exception) {
            AdLogger.e("Admob显示插页广告异常", e)
            totalShowFailCount++
            AdEventReporter.reportShowFail(AdType.INTERSTITIAL, AdPlatform.ADMOB, finalAdUnitId, totalShowFailCount, e.message.orEmpty(), sessionId = sessionId, isPreload = false)
            AdResult.Failure(createAdException("Admob interstitial: show exception: ${e.message}", e))
        }
    }

    /**
     * 基础广告加载方法（可复用）
     */
    private suspend fun loadAd(context: Context, adUnitId: String): InterstitialAd? {
        // 累积加载次数统计
        totalLoadCount++
        AdLogger.d("Admob插页广告累积加载次数: $totalLoadCount")
        
        val requestId = AdEventReporter.reportStartLoad(AdType.INTERSTITIAL, AdPlatform.ADMOB, adUnitId, totalLoadCount)
        
        return runAdmobLoadOnMainThread {
            suspendCancellableCoroutine { continuation ->
                val startTime = System.currentTimeMillis()
                
                val adRequest = AdRequest.Builder().build()

                InterstitialAd.load(context, adUnitId, adRequest, object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        val loadTime = System.currentTimeMillis() - startTime
                        AdLogger.d("Admob插页广告加载成功，广告位ID: %s, 耗时: %dms", adUnitId, loadTime)
                        totalLoadSucCount++
                        AdEventReporter.reportLoaded(AdType.INTERSTITIAL, AdPlatform.ADMOB, adUnitId, totalLoadSucCount, ad.responseInfo?.loadedAdapterResponseInfo?.adSourceName.orEmpty(), ceil(loadTime / 1000.0).toInt(), requestId)
                        
                        continuation.resume(ad)
                    }

                    override fun onAdFailedToLoad(adError: LoadAdError) {
                        val loadTime = System.currentTimeMillis() - startTime
                        AdLogger.e("Admob interstitial ad load failed，广告位ID: %s, 耗时: %dms, 错误: %s", adUnitId, loadTime, adError.message)
                        
                        totalLoadFailCount++
                        AdEventReporter.reportLoadFail(AdType.INTERSTITIAL, AdPlatform.ADMOB, adUnitId, totalLoadFailCount, adError.responseInfo?.loadedAdapterResponseInfo?.adSourceName.orEmpty(), ceil(loadTime / 1000.0).toInt(), adError.message, requestId)
                        
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
            val interstitialAd = loadAd(context, adUnitId)
            if (interstitialAd != null) {
                synchronized(adCachePool) {
                    adCachePool.add(CachedInterstitialAd(interstitialAd, adUnitId))
                    val currentCount = getCachedAdCount(adUnitId)
                    AdLogger.d("Admob插页广告加载成功并缓存，广告位ID: %s，该广告位缓存数量: %d/%d", adUnitId, currentCount, maxCacheSizePerAdUnit)
                }
                AdResult.Success(Unit)
            } else {
                AdResult.Failure(createAdException("Admob interstitial ad load returned null"))
            }
        } catch (e: Exception) {
            AdLogger.e("Admob插页loadAdToCache异常", e)
            AdResult.Failure(AdException(0, "Admob interstitial ad loadAdToCache exception: ${e.message}", e))
        }
    }

    /**
     * 从缓存获取广告
     */
    private fun getCachedAd(adUnitId: String): CachedInterstitialAd? {
        synchronized(adCachePool) {
            val index = adCachePool.indexOfFirst { it.adUnitId == adUnitId && !it.isExpired() }
            return if (index != -1) {
                adCachePool.removeAt(index)
            } else {
                null
            }
        }
    }

    /**
     * 仅查看缓存（不移除）以获取指定广告位的一个广告。
     */
    fun getCachedAdPeek(adUnitId: String): CachedInterstitialAd? {
        synchronized(adCachePool) {
            val index = adCachePool.indexOfFirst { it.adUnitId == adUnitId && !it.isExpired() }
            return if (index != -1) adCachePool[index] else null
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
    private suspend fun showAdInternal(activity: FragmentActivity, interstitialAd: InterstitialAd, adUnitId: String, sessionId: String = "", isPreload: Boolean = false): AdResult<Unit> {
        // 记录当前显示的广告（用于资源释放）
        currentShowingAd = interstitialAd
        
        return suspendCancellableCoroutine { continuation ->
            // 保存 continuation 用于生命周期清理
            showContinuation = continuation
            continuation.invokeOnCancellation {
                showContinuation = null
                isShowing = false
            }

            var currentAdUniqueId = ""

            interstitialAd.onPaidEventListener = OnPaidEventListener { value ->
                AdLogger.d("Admob插页广告收益回调: value=${value.valueMicros}, currency=${value.currencyCode}")

                val uuid = java.util.UUID.randomUUID().toString()
                val creativeId = interstitialAd.responseInfo?.loadedAdapterResponseInfo?.adSourceInstanceId.orEmpty()
                currentAdUniqueId = "${uuid}_${adUnitId}_${creativeId}"
                currentAdValue = value

                AdEventReporter.reportImpression(AdType.INTERSTITIAL, AdPlatform.ADMOB, adUnitId, currentAdUniqueId, totalShowCount, interstitialAd.responseInfo?.loadedAdapterResponseInfo?.adSourceName.orEmpty(), currentAdValue?.let { it.valueMicros / 1_000_000.0 } ?: 0.0, currentAdValue?.currencyCode ?: "", sessionId = sessionId, isPreload = isPreload)
                
                AdRevenueReporter.reportRevenue(AdType.INTERSTITIAL, AdPlatform.ADMOB, adUnitId, value.valueMicros / 1_000_000.0, value.currencyCode, interstitialAd.responseInfo?.loadedAdapterResponseInfo?.adSourceName ?: "Admob", interstitialAd.responseInfo?.loadedAdapterResponseInfo?.adSourceInstanceName.orEmpty())
            }

            interstitialAd.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    AdLogger.d("Admob插页广告关闭")
                    
                    // 设置广告不再显示标识
                    isShowing = false
                    
                    totalCloseCount++
                    
                    AdEventReporter.reportClose(AdType.INTERSTITIAL, AdPlatform.ADMOB, adUnitId, totalCloseCount, interstitialAd.responseInfo?.loadedAdapterResponseInfo?.adSourceName.orEmpty(), currentAdValue?.let { it.valueMicros / 1_000_000.0 } ?: 0.0, currentAdValue?.currencyCode ?: "")
                    
                    // 销毁广告对象，避免内存泄露
                    interstitialAd.onPaidEventListener = null
                    interstitialAd.fullScreenContentCallback = null
                    
                    // 异步预加载下一个广告到缓存（如果缓存未满）
                    if (!isCacheFull(adUnitId)) {
                        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                            try {
                                AdLogger.d("Admob插页广告关闭，开始重新预缓存，广告位ID: %s", adUnitId)
                                preloadAd(activity.applicationContext, adUnitId)
                            } catch (e: Exception) {
                                AdLogger.e("Admob插页广告重新预缓存失败", e)
                            }
                        }
                    }
                    
                    val result = AdResult.Success(Unit)
                    if (continuation.isActive) {
                        continuation.resume(result)
                    }
                }

                override fun onAdFailedToShowFullScreenContent(fullScreenContentError: AdError) {
                    AdLogger.w("Admob插页广告show failed: %s", fullScreenContentError.message)
                    
                    // 累积展示失败次数统计
                    totalShowFailCount++
                    AdLogger.d("Admob插页广告累积展示失败次数: $totalShowFailCount")
                    
                    AdEventReporter.reportShowFail(AdType.INTERSTITIAL, AdPlatform.ADMOB, adUnitId, totalShowFailCount, fullScreenContentError.message, sessionId = sessionId, isPreload = isPreload)
                    
                    // 销毁广告对象，避免内存泄露
                    interstitialAd.onPaidEventListener = null
                    interstitialAd.fullScreenContentCallback = null
                    
                    // 异步预加载下一个广告到缓存（如果缓存未满）
                    if (!isCacheFull(adUnitId)) {
                        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                            try {
                                AdLogger.d("Admob插页广告展示失败，开始重新预缓存，广告位ID: %s", adUnitId)
                                preloadAd(activity.applicationContext, adUnitId)
                            } catch (e: Exception) {
                                AdLogger.e("Admob插页广告重新预缓存失败", e)
                            }
                        }
                    }
                    
                    val result = AdResult.Failure(createAdException("Admob interstitial: show callback failed: ${fullScreenContentError.message}"))
                    if (continuation.isActive) {
                        continuation.resume(result)
                    }
                }

                override fun onAdShowedFullScreenContent() {
                    AdLogger.d("Admob插页广告开始显示")
                    AdConfigManager.recordShow(AdType.INTERSTITIAL, AdPlatform.ADMOB)
                }

                override fun onAdClicked() {
                    AdLogger.d("Admob插页广告被点击")
                    
                    // 累积点击统计
                    totalClickCount++
                    AdLogger.d("Admob插页广告累积点击次数: $totalClickCount")
                    
                    AdConfigManager.recordClick(AdType.INTERSTITIAL, AdPlatform.ADMOB)

                    // 记录点击用于重复点击保护
                    AdClickProtectionController.recordClick(currentAdUniqueId)
                    
                    AdEventReporter.reportClick(AdType.INTERSTITIAL, AdPlatform.ADMOB, adUnitId, currentAdUniqueId, totalClickCount, interstitialAd.responseInfo?.loadedAdapterResponseInfo?.adSourceName.orEmpty(), currentAdValue?.let { it.valueMicros / 1_000_000.0 } ?: 0.0, currentAdValue?.currencyCode ?: "")
                }

                override fun onAdImpression() {
                    AdLogger.d("Admob插页广告展示完成")
                    
                    // 设置广告正在显示标识
                    isShowing = true

                    // 累积展示统计
                    totalShowCount++
                    AdLogger.d("Admob插页广告累积展示次数: $totalShowCount")
                }
            }

            interstitialAd.show(activity)
        }
    }

    /**
     * 销毁广告
     */
    fun destroyAd() {
        synchronized(adCachePool) {
            adCachePool.clear()
        }
        AdLogger.d("插页广告已销毁")
    }
    
    /**
     * 销毁正在显示的广告
     * 在Activity销毁或广告关闭时调用，释放正在显示的插页广告资源
     */
    fun destroyShowingAd() {
        currentShowingAd?.let { ad ->
            ad.onPaidEventListener = null
            ad.fullScreenContentCallback = null
            AdLogger.d("Admob插页广告正在显示的广告已销毁")
        }
        currentShowingAd = null
    }
    

    /**
     * 销毁控制器
     */
    fun destroy() {
        destroyAd()
        AdLogger.d("插页广告控制器已清理")
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
     * 获取插页广告是否正在显示的状态
     * @return true 如果插页广告正在显示，false 否则
     */
    fun isAdShowing(): Boolean {
        return isShowing
    }
} 
