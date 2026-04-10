package com.android.common.bill.ads.admob

import android.content.Context
import androidx.fragment.app.FragmentActivity
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdValue
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.OnPaidEventListener
import com.google.android.gms.ads.appopen.AppOpenAd
import com.android.common.bill.BillConfig
import com.android.common.bill.ads.AdException
import com.android.common.bill.ads.AdResult
import com.android.common.bill.ads.config.AdConfigManager
import com.android.common.bill.ads.config.AdPlatform
import com.android.common.bill.ads.config.AdType
import com.android.common.bill.ads.protection.AdClickProtectionController
import com.android.common.bill.ads.log.AdLogger
import com.android.common.bill.ads.tracker.AdEventReporter
import com.android.common.bill.ads.util.AdDestroyManager
import com.android.common.bill.ads.util.AdLifecycleGuard
import com.android.common.bill.ads.util.PositionGet
import com.android.common.bill.ads.util.runAdmobLoadOnMainThread
import com.android.common.bill.ads.tracker.AdRevenueReporter
import net.corekit.core.ext.DataStoreIntDelegate
import net.corekit.core.report.ReportDataManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.ceil


/**
 * 开屏广告控制器
 * 专门处理开屏广告的加载和显示，包含广告过期逻辑
 */
class AdmobAppOpenAdController private constructor() {
    
    // 累积点击统计（持久化）
    private var totalClickCount by DataStoreIntDelegate("app_open_ad_total_clicks", 0)

    // 累积关闭统计（持久化）
    private var totalCloseCount by DataStoreIntDelegate("app_open_ad_total_close", 0)
    
    // 累积加载次数统计（持久化）
    private var totalLoadCount by DataStoreIntDelegate("app_open_ad_total_loads", 0)

    // 累积加载成功次数统计（持久化）
    private var totalLoadSucCount by DataStoreIntDelegate("app_open_ad_total_load_suc", 0)

    // 累积加载失败次数统计（持久化）
    private var totalLoadFailCount by DataStoreIntDelegate("app_open_ad_total_load_fails", 0)
    
    // 累积展示失败次数统计（持久化）
    private var totalShowFailCount by DataStoreIntDelegate("app_open_ad_total_show_fails", 0)
    
    // 累积触发统计（持久化）
    private var totalShowTriggerCount by DataStoreIntDelegate("app_open_ad_total_show_triggers", 0)
    
    // 累积展示统计（持久化）
    private var totalShowCount by DataStoreIntDelegate("app_open_ad_total_shows", 0)
    
    // 当前正在显示的广告（用于资源释放）
    private var currentShowingAd: AppOpenAd? = null
    
    // 展示结果回调
    private var showContinuation: kotlinx.coroutines.CancellableContinuation<AdResult<Unit>>? = null
    
    companion object {
        private const val TAG = "AdmobAppOpenAdController"
        private const val AD_TIMEOUT = 4 * 60 * 60 * 1000L // 4小时过期
        private const val DEFAULT_CACHE_SIZE_PER_AD_UNIT = 2
        
        @Volatile
        private var INSTANCE: AdmobAppOpenAdController? = null
        
        fun getInstance(): AdmobAppOpenAdController {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AdmobAppOpenAdController().also { INSTANCE = it }
            }
        }
    }
    
    // 内存缓存池 - 存储预加载的广告
    private val adCachePool = mutableListOf<CachedAppOpenAd>()
    private val maxCacheSizePerAdUnit = DEFAULT_CACHE_SIZE_PER_AD_UNIT
    

    /**
     * 缓存的开屏广告数据类
     */
    data class CachedAppOpenAd(
        val ad: AppOpenAd,
        val adUnitId: String,
        val loadTime: Long = System.currentTimeMillis()
    ) {
        fun isExpired(): Boolean {
            return System.currentTimeMillis() - loadTime > AD_TIMEOUT
        }
    }
    
    /**
     * 预加载开屏广告
     * @param context 上下文
     * @param adUnitId 广告位ID，如果为空则使用默认ID
     */
    suspend fun preloadAd(context: Context, adUnitId: String? = null): AdResult<Unit> {
        val finalAdUnitId = adUnitId ?: BillConfig.admob.splashId
        return loadAdToCache(context, finalAdUnitId)
    }

    /**
     * 基础广告加载方法（可复用）
     */
    private suspend fun loadAd(context: Context, adUnitId: String): AppOpenAd? {
        // 累积加载次数统计
        totalLoadCount++
        AdLogger.d("Admob开屏广告累积加载次数: $totalLoadCount")
        
        val requestId = AdEventReporter.reportStartLoad(
            adType = AdType.APP_OPEN,
            platform = AdPlatform.ADMOB,
            adUnitId = adUnitId,
            number = totalLoadCount
        )
        return runAdmobLoadOnMainThread {
            suspendCancellableCoroutine { continuation ->
                val startTime = System.currentTimeMillis()

                val adRequest = AdRequest.Builder().build()

                val loadCallback = object : AppOpenAd.AppOpenAdLoadCallback() {
                    override fun onAdLoaded(ad: AppOpenAd) {
                        val loadTime = System.currentTimeMillis() - startTime
                        AdLogger.d("Admob开屏广告加载成功，广告位ID: %s, 耗时: %dms", adUnitId, loadTime)
                        totalLoadSucCount++
                        AdEventReporter.reportLoaded(
                            adType = AdType.APP_OPEN,
                            platform = AdPlatform.ADMOB,
                            adUnitId = adUnitId,
                            number = totalLoadSucCount,
                            adSource = ad.responseInfo?.loadedAdapterResponseInfo?.adSourceName.orEmpty(),
                            passTime = ceil(loadTime / 1000.0).toInt(),
                            requestId = requestId
                        )
                        continuation.resume(ad)
                    }

                    override fun onAdFailedToLoad(adError: LoadAdError) {
                        val loadTime = System.currentTimeMillis() - startTime
                        AdLogger.e("Admob app open ad load failed，广告位ID: %s, 耗时: %dms, 错误: %s", adUnitId, loadTime, adError.message)
                        totalLoadFailCount++
                        AdEventReporter.reportLoadFail(
                            adType = AdType.APP_OPEN,
                            platform = AdPlatform.ADMOB,
                            adUnitId = adUnitId,
                            number = totalLoadFailCount,
                            adSource = adError.responseInfo?.loadedAdapterResponseInfo?.adSourceName.orEmpty(),
                            passTime = ceil(loadTime / 1000.0).toInt(),
                            reason = adError.message,
                            requestId = requestId
                        )
                        continuation.resume(null)
                    }
                }

                // 启动广告加载
                AppOpenAd.load(context, adUnitId, adRequest, loadCallback)
            }
        }
    }
    
    /**
     * 加载广告到缓存
     */
    private suspend fun loadAdToCache(context: Context, adUnitId: String): AdResult<Unit> {
        return try {
            
            // 检查缓存是否已满
            val currentAdUnitCount = getCachedAdCount(adUnitId)
            if (currentAdUnitCount >= maxCacheSizePerAdUnit) {
                AdLogger.w("Admob开屏广告位 %s 缓存已满，当前缓存: %d/%d", adUnitId, currentAdUnitCount, maxCacheSizePerAdUnit)
                return AdResult.Success(Unit)
            }
            
            // 加载广告
            val appOpenAd = loadAd(context, adUnitId)
            if (appOpenAd != null) {
                synchronized(adCachePool) {
                    adCachePool.add(CachedAppOpenAd(appOpenAd, adUnitId))
                    val currentCount = getCachedAdCount(adUnitId)
                    AdLogger.d("Admob开屏广告加载成功并缓存，广告位ID: %s，该广告位缓存数量: %d/%d", adUnitId, currentCount, maxCacheSizePerAdUnit)
                }
                AdResult.Success(Unit)
            } else {
                AdResult.Failure(createAdException("Admob app open ad load returned null"))
            }
        } catch (e: Exception) {
            AdLogger.e("Admob开屏loadAdToCache异常", e)
            AdResult.Failure(AdException(0, "Admob app open ad loadAdToCache exception: ${e.message}", e))
        }
    }
    
    /**
     * 显示开屏广告（自动处理加载和过期检查）
     * @param activity Activity上下文
     * @param adUnitId 广告位ID，如果为空则使用默认ID
     */
    suspend fun showAd(activity: FragmentActivity, adUnitId: String? = null, onLoaded:((isSuc: Boolean)->Unit)?=null, sessionId: String = ""): AdResult<Unit> {
        // 累积触发广告展示次数统计
        totalShowTriggerCount++
        AdLogger.d("Admob开屏广告累积触发展示次数: $totalShowTriggerCount")

        // 生命周期检查：等待 Activity Resume 或取消
        val lifecycleGuard = AdLifecycleGuard.instance
        when (val lifecycleResult = lifecycleGuard.awaitResumeOrCancel(activity)) {
            is AdResult.Failure -> {
                AdLogger.w("Admob开屏广告展示被取消：Activity生命周期不满足")
                totalShowFailCount++
                AdEventReporter.reportShowFail(
                    AdType.APP_OPEN,
                    AdPlatform.ADMOB,
                    adUnitId ?: BillConfig.admob.splashId,
                    totalShowFailCount,
                    lifecycleResult.error.message,
                    sessionId = sessionId,
                    isPreload = false
                )
                onLoaded?.invoke(false)
                return lifecycleResult
            }
            else -> { /* continue */ }
        }
        
        // 注册 Activity 销毁时的清理回调
        AdDestroyManager.instance.register(activity) {
            AdLogger.d("Admob开屏广告: Activity销毁，清理展示资源")
            // 销毁正在显示的广告对象
            destroyShowingAd()
            showContinuation?.let {
                if (it.isActive) {
                    totalShowFailCount++
                    AdEventReporter.reportShowFail(AdType.APP_OPEN, AdPlatform.ADMOB, adUnitId ?: BillConfig.admob.splashId, totalShowFailCount, "Activity destroyed", sessionId = sessionId, isPreload = false)
                    it.resume(AdResult.Failure(createAdException("Admob app open: Activity destroyed")))
                }
            }
            showContinuation = null
        }
        
        val finalAdUnitId = adUnitId ?: BillConfig.admob.splashId

        // 检查缓存过期
        synchronized(adCachePool) {
            if (adCachePool.any { it.adUnitId == finalAdUnitId && it.isExpired() }) {
                AdEventReporter.reportTimeoutCache(AdType.APP_OPEN, AdPlatform.ADMOB, finalAdUnitId)
            }
        }

        return try {
            // 1. 尝试从缓存获取广告
            var cachedAd = getCachedAd(finalAdUnitId)
            var isPreload = cachedAd != null

            // 2. 如果缓存为空，立即加载并缓存一个广告
            if (cachedAd == null) {
                AdLogger.d("Admob缓存为空，立即加载开屏广告，广告位ID: %s", finalAdUnitId)
                loadAdToCache(activity, finalAdUnitId)
                cachedAd = getCachedAd(finalAdUnitId)
                isPreload = false
            }

            if (cachedAd != null) {
                AdLogger.d("Admob使用缓存中的开屏广告，广告位ID: %s", finalAdUnitId)
                onLoaded?.invoke(true)
                
                // 3. 显示广告
                val result = showAdInternal(activity, cachedAd.ad, finalAdUnitId, sessionId = sessionId, isPreload = isPreload)
                
                result
            } else {
                onLoaded?.invoke(false)
                totalShowFailCount++
                AdEventReporter.reportShowFail(AdType.APP_OPEN, AdPlatform.ADMOB, finalAdUnitId, totalShowFailCount, "No ad available", sessionId = sessionId, isPreload = false)
                AdResult.Failure(createAdException("Admob app open ad no available ad"))
            }
        } catch (e: Exception) {
            AdLogger.e("Admob显示开屏广告异常", e)
            totalShowFailCount++
            AdEventReporter.reportShowFail(AdType.APP_OPEN, AdPlatform.ADMOB, finalAdUnitId, totalShowFailCount, e.message.orEmpty(), sessionId = sessionId, isPreload = false)
            AdResult.Failure(createAdException("Admob app open: show exception: ${e.message}", e))
        }
    }
    
    /**
     * 从缓存获取广告
     */
    private fun getCachedAd(adUnitId: String): CachedAppOpenAd? {
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
     * 仅查看缓存（不移除）以获取指定广告位的一个广告
     */
    fun getCachedAdPeek(adUnitId: String): CachedAppOpenAd? {
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
    private suspend fun showAdInternal(activity: FragmentActivity, appOpenAd: AppOpenAd, adUnitId: String, sessionId: String = "", isPreload: Boolean = false): AdResult<Unit> {
        // 记录当前显示的广告（用于资源释放）
        currentShowingAd = appOpenAd
        
        return suspendCancellableCoroutine { continuation ->
            // 保存 continuation 用于生命周期清理
            showContinuation = continuation
            continuation.invokeOnCancellation {
                showContinuation = null
            }

            // 临时变量保存收益数据
            var currentAdValue: AdValue? = null
            
            var currentAdUniqueId = ""

            appOpenAd.onPaidEventListener = OnPaidEventListener { value ->
                AdLogger.d("Admob开屏广告收益回调: value=${value.valueMicros}, currency=${value.currencyCode}")

                val uuid = java.util.UUID.randomUUID().toString()
                val creativeId = appOpenAd.responseInfo?.loadedAdapterResponseInfo?.adSourceInstanceId.orEmpty()
                currentAdUniqueId = "${uuid}_${adUnitId}_${creativeId}"
                currentAdValue = value

                AdEventReporter.reportImpression(
                    adType = AdType.APP_OPEN,
                    platform = AdPlatform.ADMOB,
                    adUnitId = adUnitId,
                    adUniqueId = currentAdUniqueId,
                    number = totalShowCount,
                    adSource = appOpenAd.responseInfo?.loadedAdapterResponseInfo?.adSourceName.orEmpty(),
                    value = (currentAdValue?.valueMicros ?: 0) / 1_000_000.0,
                    currency = currentAdValue?.currencyCode ?: "",
                    sessionId = sessionId,
                    isPreload = isPreload
                )

                AdRevenueReporter.reportRevenue(
                    AdType.APP_OPEN,
                    AdPlatform.ADMOB,
                    adUnitId,
                    value.valueMicros / 1_000_000.0,
                    value.currencyCode,
                    appOpenAd.responseInfo?.loadedAdapterResponseInfo?.adSourceName ?: "Admob",
                    appOpenAd.responseInfo?.loadedAdapterResponseInfo?.adSourceInstanceName.orEmpty()
                )
            }

            appOpenAd.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    totalCloseCount ++
                    AdLogger.d("Admob开屏广告关闭")
                    AdEventReporter.reportClose(
                        adType = AdType.APP_OPEN,
                        platform = AdPlatform.ADMOB,
                        adUnitId = adUnitId,
                        number = totalCloseCount,
                        adSource = appOpenAd.responseInfo?.loadedAdapterResponseInfo?.adSourceName.orEmpty(),
                        value = (currentAdValue?.valueMicros ?: 0) / 1_000_000.0,
                        currency = currentAdValue?.currencyCode ?: ""
                    )
                    
                    // 销毁广告对象，避免内存泄露
                    appOpenAd.onPaidEventListener = null
                    appOpenAd.fullScreenContentCallback = null
                    
                    // 异步预加载下一个广告到缓存（如果缓存未满）
                    if (!isCacheFull(adUnitId)) {
                        CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
                            try {
                                AdLogger.d("Admob开屏广告关闭，开始重新预缓存，广告位ID: %s", adUnitId)
                                preloadAd(activity.applicationContext, adUnitId)
                            } catch (e: Exception) {
                                AdLogger.e("Admob开屏广告重新预缓存失败", e)
                            }
                        }
                    }
                    
                    val result = AdResult.Success(Unit)
                    if (continuation.isActive) {
                        continuation.resume(result)
                    }
                }

                override fun onAdFailedToShowFullScreenContent(fullScreenContentError: AdError) {
                    AdLogger.w("Admob开屏广告show failed: %s", fullScreenContentError.message)
                    totalShowFailCount++
                    AdEventReporter.reportShowFail(
                        adType = AdType.APP_OPEN,
                        platform = AdPlatform.ADMOB,
                        adUnitId = adUnitId,
                        number = totalShowFailCount,
                        reason = fullScreenContentError.message,
                        sessionId = sessionId,
                        isPreload = isPreload
                    )
                    
                    // 销毁广告对象，避免内存泄露
                    appOpenAd.onPaidEventListener = null
                    appOpenAd.fullScreenContentCallback = null
                    
                    // 异步预加载下一个广告到缓存（如果缓存未满）
                    if (!isCacheFull(adUnitId)) {
                        CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
                            try {
                                AdLogger.d("Admob开屏广告展示失败，开始重新预缓存，广告位ID: %s", adUnitId)
                                preloadAd(activity.applicationContext, adUnitId)
                            } catch (e: Exception) {
                                AdLogger.e("Admob开屏广告重新预缓存失败", e)
                            }
                        }
                    }
                    
                    val result = AdResult.Failure(createAdException("Admob app open: show callback failed: ${fullScreenContentError.message}"))
                    if (continuation.isActive) {
                        continuation.resume(result)
                    }
                }

                override fun onAdShowedFullScreenContent() {
                    AdLogger.d("Admob开屏广告开始显示")
                    
                    // 累积展示统计
                    totalShowCount++
                    AdLogger.d("Admob开屏广告累积展示次数: $totalShowCount")
                    
                    AdConfigManager.recordShow(AdType.APP_OPEN, AdPlatform.ADMOB)
                }

                override fun onAdClicked() {
                    AdLogger.d("Admob开屏广告被点击")
                    
                    // 累积点击统计
                    totalClickCount++
                    AdLogger.d("Admob开屏广告累积点击次数: $totalClickCount")
                    AdLogger.d("开屏广告点击时收益数据: ${if (currentAdValue != null) "value=${currentAdValue!!.valueMicros}, currency=${currentAdValue!!.currencyCode}" else "暂无收益数据"}")
                    
                    AdConfigManager.recordClick(AdType.APP_OPEN, AdPlatform.ADMOB)

                    // 记录点击用于重复点击保护
                    AdClickProtectionController.recordClick(currentAdUniqueId)

                    AdEventReporter.reportClick(
                        adType = AdType.APP_OPEN,
                        platform = AdPlatform.ADMOB,
                        adUnitId = adUnitId,
                        adUniqueId = currentAdUniqueId,
                        number = totalClickCount,
                        adSource = appOpenAd.responseInfo?.loadedAdapterResponseInfo?.adSourceName.orEmpty(),
                        value = (currentAdValue?.valueMicros ?: 0) / 1_000_000.0,
                        currency = currentAdValue?.currencyCode ?: ""
                    )
                }

                override fun onAdImpression() {
                    AdLogger.d("开屏广告展示完成")
                }
            }
            
            appOpenAd.show(activity)
        }
    }


    
    /**
     * 销毁广告
     */
    fun destroyAd() {
        synchronized(adCachePool) {
            adCachePool.clear()
        }
        AdLogger.d("开屏广告已销毁")
    }
    
    /**
     * 销毁正在显示的广告
     * 在Activity销毁或广告关闭时调用，释放正在显示的开屏广告资源
     */
    fun destroyShowingAd() {
        currentShowingAd?.let { ad ->
            ad.onPaidEventListener = null
            ad.fullScreenContentCallback = null
            AdLogger.d("Admob开屏广告正在显示的广告已销毁")
        }
        currentShowingAd = null
    }
    
    /**
     * 销毁控制器
     */
    fun destroy() {
        destroyAd()
        AdLogger.d("开屏广告控制器已清理")
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
