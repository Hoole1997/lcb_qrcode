package com.android.common.bill.ads.topon

import android.content.Context
import androidx.fragment.app.FragmentActivity
import android.view.ViewGroup
import com.android.common.bill.BillConfig
import com.android.common.bill.ads.AdException
import com.android.common.bill.ads.AdResult
import com.android.common.bill.ads.config.AdConfigManager
import com.android.common.bill.ads.config.AdPlatform
import com.android.common.bill.ads.config.AdType
import com.android.common.bill.ads.log.AdLogger
import com.android.common.bill.ads.protection.AdClickProtectionController
import com.android.common.bill.ads.tracker.AdEventReporter
import com.android.common.bill.ads.tracker.AdRevenueReporter
import com.android.common.bill.ads.util.AdDestroyManager
import com.android.common.bill.ads.util.AdLifecycleGuard
import com.android.common.bill.ads.util.PositionGet
import net.corekit.core.ads.RevenueAdData
import net.corekit.core.ads.RevenueAdManager
import net.corekit.core.ads.RevenueInfo
import net.corekit.core.ext.DataStoreIntDelegate
import net.corekit.core.report.ReportDataManager
import com.thinkup.core.api.AdError
import com.thinkup.core.api.TUAdInfo
import com.thinkup.splashad.api.TUSplashAd
import com.thinkup.splashad.api.TUSplashAdEZListener
import com.thinkup.splashad.api.TUSplashAdExtraInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.ceil

/**
 * TopOn 开屏广告控制器
 * 参考 AdMob 开屏广告控制器实现，保持埋点一致
 */
class TopOnSplashAdController private constructor() {

    // 当前展示的sessionId和isPreload
    private var currentSessionId: String = ""
    private var currentIsPreload: Boolean = false

    // 累积统计（持久化）
    private var totalClickCount by DataStoreIntDelegate("topon_splash_ad_total_clicks", 0)
    private var totalCloseCount by DataStoreIntDelegate("topon_splash_ad_total_close", 0)
    private var totalLoadCount by DataStoreIntDelegate("topon_splash_ad_total_loads", 0)
    private var totalLoadSucCount by DataStoreIntDelegate("topon_splash_ad_total_load_suc", 0)
    private var totalLoadFailCount by DataStoreIntDelegate("topon_splash_ad_total_load_fails", 0)
    private var totalShowFailCount by DataStoreIntDelegate("topon_splash_ad_total_show_fails", 0)
    private var totalShowTriggerCount by DataStoreIntDelegate("topon_splash_ad_total_show_triggers", 0)
    private var totalShowCount by DataStoreIntDelegate("topon_splash_ad_total_shows", 0)

    companion object {
        private const val TAG = "TopOnSplashAdController"
        private const val AD_TIMEOUT = 4 * 60 * 60 * 1000L // 4小时过期
        private const val DEFAULT_CACHE_SIZE_PER_AD_UNIT = 1
        private const val DEFAULT_FETCH_AD_TIMEOUT = 8000 // 8秒超时

        @Volatile
        private var INSTANCE: TopOnSplashAdController? = null

        fun getInstance(): TopOnSplashAdController {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TopOnSplashAdController().also { INSTANCE = it }
            }
        }
    }

    // 内存缓存池 - 存储预加载的广告
    private val adCachePool = mutableListOf<CachedSplashAd>()
    private val maxCacheSizePerAdUnit = DEFAULT_CACHE_SIZE_PER_AD_UNIT

    // 存储每个 placementId 对应的 continuation，用于在 onAdDismiss 回调中恢复
    private val continuationMap = mutableMapOf<String, kotlinx.coroutines.CancellableContinuation<AdResult<Unit>>>()
    
    // 当前正在显示的广告（用于资源释放）
    private var currentShowingAd: TUSplashAd? = null
    
    // 当前广告显示的容器（用于手动移除广告视图）
    private var currentAdContainer: ViewGroup? = null
    
    // 显示广告前容器的子视图数量
    private var containerChildCountBeforeShow: Int = 0

    /**
     * 缓存的开屏广告数据类
     */
    private data class CachedSplashAd(
        val splashAd: TUSplashAd,
        val placementId: String,
        val loadTime: Long = System.currentTimeMillis()
    ) {
        fun isExpired(): Boolean {
            return System.currentTimeMillis() - loadTime > AD_TIMEOUT || !splashAd.isAdReady
        }
    }

    /**
     * 预加载开屏广告
     * @param context 上下文
     * @param placementId 广告位ID，如果为空则使用默认ID
     */
    suspend fun preloadAd(context: Context, placementId: String? = null): AdResult<Unit> {
        val finalPlacementId = placementId ?: BillConfig.topon.splashId

        // 检查是否已有有效缓存且广告已就绪
        val cachedAd = peekCachedAd(finalPlacementId)
        if (cachedAd != null) {
            AdLogger.d("TopOn开屏广告已有有效缓存且已就绪，广告位ID: %s，跳过加载", finalPlacementId)
            return AdResult.Success(Unit)
        }

        return loadAdToCache(context, finalPlacementId)
    }

    /**
     * 基础广告加载方法（可复用）
     */
    private suspend fun loadAd(context: Context, placementId: String, fetchAdTimeout: Int = DEFAULT_FETCH_AD_TIMEOUT): AdResult<TUSplashAd> {
        // 累积加载次数统计
        totalLoadCount++
        AdLogger.d("TopOn开屏广告累积加载次数: $totalLoadCount")

        val requestId = AdEventReporter.reportStartLoad(AdType.APP_OPEN, AdPlatform.TOPON, placementId, totalLoadCount)

        return suspendCancellableCoroutine { continuation ->
            val startTime = System.currentTimeMillis()
            val applicationContext = context.applicationContext

            // 将 splashAd 声明在外部作用域，以便在回调中访问
            var splashAd: TUSplashAd? = null
            var currentAdUniqueId = ""

            try {
                splashAd = TUSplashAd(
                    applicationContext,
                    placementId,
                    object : TUSplashAdEZListener() {
                        override fun onAdLoaded() {
                            val loadTime = System.currentTimeMillis() - startTime
                            AdLogger.d("TopOn开屏广告加载成功，广告位ID: %s, 耗时: %dms", placementId, loadTime)
                            totalLoadSucCount++
                            AdEventReporter.reportLoaded(AdType.APP_OPEN, AdPlatform.TOPON, placementId, totalLoadSucCount, "", ceil(loadTime / 1000.0).toInt(), requestId)
                            splashAd?.let { continuation.resume(AdResult.Success(it)) }
                                ?: continuation.resume(AdResult.Failure(createAdException("splashAd is null")))
                        }

                        override fun onNoAdError(adError: AdError) {
                            val loadTime = System.currentTimeMillis() - startTime
                            val errorMsg = adError.desc ?: adError.getFullErrorInfo()
                            AdLogger.e("TopOnapp open ad load failed，广告位ID: %s, 耗时: %dms, 错误: %s", placementId, loadTime, adError.getFullErrorInfo())
                            totalLoadFailCount++
                            AdEventReporter.reportLoadFail(AdType.APP_OPEN, AdPlatform.TOPON, placementId, totalLoadFailCount, "", ceil(loadTime / 1000.0).toInt(), errorMsg, requestId)
                            continuation.resume(AdResult.Failure(AdException(adError.code.toInt(), errorMsg)))
                        }

                        override fun onAdShow(adInfo: TUAdInfo) {
                            AdLogger.d("TopOn开屏广告开始显示")
                            totalShowCount++
                            AdLogger.d("TopOn开屏广告累积展示次数: $totalShowCount")
                            AdConfigManager.recordShow(AdType.APP_OPEN, AdPlatform.TOPON)

                            currentAdUniqueId = "${java.util.UUID.randomUUID()}_${adInfo.placementId}_${adInfo.adsourceId}"
                            
                            // 处理收益信息
                            val revenueValue = adInfo.publisherRevenue ?: adInfo.ecpm ?: 0.0
                            val revenueCurrency = adInfo.currency ?: "USD"
                            
                            AdEventReporter.reportImpression(AdType.APP_OPEN, AdPlatform.TOPON, placementId, currentAdUniqueId, totalShowCount, adInfo.networkName ?: "", revenueValue, revenueCurrency, sessionId = currentSessionId, isPreload = currentIsPreload)
                            
                            // 上报真实的广告收益数据
                            AdRevenueReporter.reportRevenue(AdType.APP_OPEN, AdPlatform.TOPON, placementId, adInfo.publisherRevenue ?: 0.0, "USD", adInfo.networkName, adInfo.placementId)
                            
                            // TopOn 的 revenueValue 已经是美元，直接使用
                            val revenueUsd = revenueValue.toLong()
                        }

                        override fun onAdClick(adInfo: TUAdInfo) {
                            AdLogger.d("TopOn开屏广告被点击")
                            totalClickCount++
                            AdLogger.d("TopOn开屏广告累积点击次数: $totalClickCount")

                            AdClickProtectionController.recordClick(currentAdUniqueId)
                            AdConfigManager.recordClick(AdType.APP_OPEN, AdPlatform.TOPON)
                            AdEventReporter.reportClick(AdType.APP_OPEN, AdPlatform.TOPON, placementId, currentAdUniqueId, totalClickCount, adInfo.networkName ?: "", adInfo.publisherRevenue ?: 0.0, "USD")
                        }


                        override fun onAdDismiss(adInfo: TUAdInfo, splashAdExtraInfo: TUSplashAdExtraInfo) {
                            AdLogger.d("TopOn开屏广告关闭")
                            totalCloseCount++
                            AdEventReporter.reportClose(AdType.APP_OPEN, AdPlatform.TOPON, placementId, totalCloseCount, adInfo.networkName ?: "", adInfo.publisherRevenue ?: adInfo.ecpm ?: 0.0, adInfo.currency ?: "USD")

                            // 手动从容器中移除广告视图
                            try {
                                currentAdContainer?.let { container ->
                                    // 移除显示广告后新增的所有视图
                                    val currentCount = container.childCount
                                    if (currentCount > containerChildCountBeforeShow) {
                                        // 从后往前移除，避免索引问题
                                        for (i in currentCount - 1 downTo containerChildCountBeforeShow) {
                                            val child = container.getChildAt(i)
                                            container.removeViewAt(i)
                                            AdLogger.d("TopOn开屏广告视图已从容器中移除，索引: %d, 类名: %s", i, child.javaClass.name)
                                        }
                                    }
                                }
                                currentAdContainer = null
                                containerChildCountBeforeShow = 0
                                AdLogger.d("TopOn开屏广告容器已清理")
                            } catch (e: Exception) {
                                AdLogger.e("TopOn开屏广告视图移除异常", e)
                            }
                            
                            // 清理当前显示的广告引用
                            currentShowingAd = null

                            // 恢复 continuation（在 showAdInternal 中设置）
                            synchronized(continuationMap) {
                                continuationMap.remove(placementId)?.let { cont ->
                                    if (cont.isActive) {
                                        cont.resume(AdResult.Success(Unit))
                                    }
                                }
                            }
                            
                            // 广告关闭后，如果缓存未满，异步预加载下一个广告
                            if (!isCacheFull(placementId)) {
                                AdLogger.d("TopOn开屏广告关闭后开始异步预加载下一个广告，广告位ID: %s", placementId)
                                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                                    try {
                                        preloadAd(applicationContext, placementId)
                                    } catch (e: Exception) {
                                        AdLogger.e("TopOn开屏广告预加载失败", e)
                                    }
                                }
                            }
                        }
                    },
                    fetchAdTimeout
                )

                splashAd.loadAd()
            } catch (e: Exception) {
                AdLogger.e("TopOn开屏广告create exception", e)
                totalLoadFailCount++
                AdEventReporter.reportLoadFail(AdType.APP_OPEN, AdPlatform.TOPON, placementId, totalLoadFailCount, "", 0, e.message.orEmpty(), requestId)
                if (continuation.isActive) {
                    continuation.resume(AdResult.Failure(createAdException("TopOn splash: create exception: ${e.message}", e)))
                }
            }
        }
    }

    /**
     * 加载广告到缓存
     */
    suspend fun loadAdToCache(context: Context, placementId: String): AdResult<Unit> {
        return try {
            // 检查缓存是否已满
            val currentPlacementCount = getCachedAdCount(placementId)
            if (currentPlacementCount >= maxCacheSizePerAdUnit) {
                AdLogger.w("广告位 %s 缓存已满，当前缓存: %d/%d", placementId, currentPlacementCount, maxCacheSizePerAdUnit)
                return AdResult.Success(Unit)
            }

            // 加载广告
            when (val loadResult = loadAd(context, placementId)) {
                is AdResult.Success -> {
                    synchronized(adCachePool) {
                        adCachePool.add(CachedSplashAd(loadResult.data, placementId))
                        val currentCount = getCachedAdCount(placementId)
                        AdLogger.d("TopOn开屏广告加载成功并缓存，广告位ID: %s，该广告位缓存数量: %d/%d", placementId, currentCount, maxCacheSizePerAdUnit)
                    }
                    AdResult.Success(Unit)
                }
                is AdResult.Failure -> AdResult.Failure(createAdException("TopOnapp open ad load returned non-success state"))
            }
        } catch (e: Exception) {
            AdLogger.e("TopOn开屏loadAdToCache异常", e)
            AdResult.Failure(AdException(0, "TopOn splash ad loadAdToCache exception: ${e.message}", e))
        }
    }

    /**
     * 显示开屏广告（自动处理加载和过期检查）
     * @param activity Activity上下文
     * @param placementId 广告位ID，如果为空则使用默认ID
     * @param onLoaded 加载状态回调
     */
    suspend fun showAd(
        activity: FragmentActivity,
        placementId: String = BillConfig.topon.splashId,
        onLoaded: ((isSuc: Boolean) -> Unit)? = null,
        sessionId: String = ""
    ): AdResult<Unit> {
        // 累积触发广告展示次数统计
        totalShowTriggerCount++
        AdLogger.d("TopOn开屏广告累积触发展示次数: $totalShowTriggerCount")

        // 生命周期检查：等待 Activity Resume 或取消
        val lifecycleGuard = AdLifecycleGuard.instance
        when (val lifecycleResult = lifecycleGuard.awaitResumeOrCancel(activity)) {
            is AdResult.Failure -> {
                AdLogger.w("TopOn开屏广告展示被取消：Activity生命周期不满足")
                totalShowFailCount++
                AdEventReporter.reportShowFail(
                    AdType.APP_OPEN,
                    AdPlatform.TOPON,
                    placementId,
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
            AdLogger.d("TopOn开屏广告: Activity销毁，清理展示资源")
            // 销毁正在显示的广告对象
            destroyShowingAd()
            synchronized(continuationMap) {
                continuationMap[placementId]?.let {
                    if (it.isActive) {
                        totalShowFailCount++
                        AdEventReporter.reportShowFail(AdType.APP_OPEN, AdPlatform.TOPON, placementId, totalShowFailCount, "Activity destroyed", sessionId = sessionId, isPreload = false)
                        it.resume(AdResult.Failure(createAdException("TopOn splash: Activity destroyed")))
                    }
                }
                continuationMap.remove(placementId)
            }
        }

        val finalPlacementId = placementId

        currentSessionId = sessionId
        currentIsPreload = peekCachedAd(finalPlacementId) != null

        // 检查缓存过期
        synchronized(adCachePool) {
            if (adCachePool.any { it.placementId == finalPlacementId && it.isExpired() }) {
                AdEventReporter.reportTimeoutCache(AdType.APP_OPEN, AdPlatform.TOPON, finalPlacementId)
            }
        }

        val adResult = try {
            // 1. 尝试从缓存获取广告
            var cachedAd = getCachedAd(finalPlacementId)

            // 2. 如果缓存为空，立即加载并缓存一个广告
            if (cachedAd == null) {
                AdLogger.d("缓存为空，立即加载TopOn开屏广告，广告位ID: %s", finalPlacementId)
                when (val loadResult = loadAdToCache(activity, finalPlacementId)) {
                    is AdResult.Success -> cachedAd = getCachedAd(finalPlacementId)
                    is AdResult.Failure -> {
                        onLoaded?.invoke(false)
                        totalShowFailCount++
                        AdEventReporter.reportShowFail(AdType.APP_OPEN, AdPlatform.TOPON, finalPlacementId, totalShowFailCount, loadResult.error.message, sessionId = currentSessionId, isPreload = currentIsPreload)
                        return loadResult
                    }
                }
            }

            if (cachedAd != null) {
                AdLogger.d("使用缓存中的TopOn开屏广告，广告位ID: %s", finalPlacementId)
                onLoaded?.invoke(true)
                
                // 3. 获取容器并显示广告
                val container = activity.window.decorView.findViewById<ViewGroup>(android.R.id.content)
                    ?: activity.window.decorView as ViewGroup
                
                // 记录当前显示的广告和容器（用于资源释放）
                currentShowingAd = cachedAd.splashAd
                currentAdContainer = container
                containerChildCountBeforeShow = container.childCount
                
                val result = showAdInternal(activity, container, cachedAd.splashAd, finalPlacementId)

                result
            } else {
                onLoaded?.invoke(false)
                totalShowFailCount++
                AdEventReporter.reportShowFail(AdType.APP_OPEN, AdPlatform.TOPON, finalPlacementId, totalShowFailCount, "No ad available", sessionId = currentSessionId, isPreload = currentIsPreload)
                AdResult.Failure(createAdException("TopOnapp open ad no available ad"))
            }
        } catch (e: Exception) {
            AdLogger.e("显示TopOn开屏广告异常", e)
            totalShowFailCount++
            AdEventReporter.reportShowFail(AdType.APP_OPEN, AdPlatform.TOPON, finalPlacementId, totalShowFailCount, e.message.orEmpty(), sessionId = currentSessionId, isPreload = currentIsPreload)
            AdResult.Failure(createAdException("TopOn splash: show exception: ${e.message}", e))
        }

        return adResult
    }

    /**
     * 从缓存获取广告
     */
    private fun getCachedAd(placementId: String): CachedSplashAd? {
        synchronized(adCachePool) {
            val index = adCachePool.indexOfFirst { it.placementId == placementId && !it.isExpired() }
            return if (index != -1) {
                adCachePool.removeAt(index)
            } else {
                null
            }
        }
    }

    fun peekCachedAd(placementId: String = BillConfig.topon.splashId): TUSplashAd? {
        return synchronized(adCachePool) {
            adCachePool.firstOrNull { it.placementId == placementId && !it.isExpired() }?.splashAd
        }
    }

    /**
     * 获取指定广告位的缓存数量
     */
    private fun getCachedAdCount(placementId: String): Int {
        synchronized(adCachePool) {
            return adCachePool.count { it.placementId == placementId && !it.isExpired() }
        }
    }

    /**
     * 检查指定广告位缓存是否已满
     */
    private fun isCacheFull(placementId: String): Boolean {
        return getCachedAdCount(placementId) >= maxCacheSizePerAdUnit
    }

    /**
     * 显示广告的内部实现
     */
    private suspend fun showAdInternal(
        activity: FragmentActivity,
        container: ViewGroup,
        splashAd: TUSplashAd,
        placementId: String
    ): AdResult<Unit> {
        return suspendCancellableCoroutine { continuation ->
            try {
                // 检查广告是否准备好
                if (!splashAd.isAdReady) {
                    AdLogger.w("TopOn开屏ad not ready，广告位ID: %s", placementId)
                    totalShowFailCount++
                    AdEventReporter.reportShowFail(AdType.APP_OPEN, AdPlatform.TOPON, placementId, totalShowFailCount, "ad not ready", sessionId = currentSessionId, isPreload = currentIsPreload)
                    if (continuation.isActive) {
                        continuation.resume(AdResult.Failure(createAdException("TopOnapp open ad not ready, isAdReady=false")))
                    }
                    return@suspendCancellableCoroutine
                }

                // 存储 continuation，以便在 onAdDismiss 回调中使用
                synchronized(continuationMap) {
                    continuationMap[placementId] = continuation
                }

                // 显示广告
                // onAdDismiss 回调已在 loadAd 中设置，会在广告关闭时恢复 continuation
                splashAd.show(activity, container)
            } catch (e: Exception) {
                AdLogger.e("TopOn开屏广告show exception", e)
                totalShowFailCount++
                AdEventReporter.reportShowFail(AdType.APP_OPEN, AdPlatform.TOPON, placementId, totalShowFailCount, e.message ?: "show exception", sessionId = currentSessionId, isPreload = currentIsPreload)
                // 清理 continuation
                synchronized(continuationMap) {
                    continuationMap.remove(placementId)
                }

                // 展示失败后重新预缓存
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        AdLogger.d("TopOn开屏广告show exception，开始重新预缓存，广告位ID: %s", placementId)
                        preloadAd(activity.applicationContext, placementId)
                    } catch (ex: Exception) {
                        AdLogger.e("TopOn开屏广告重新预缓存失败", ex)
                    }
                }

                if (continuation.isActive) {
                    continuation.resume(AdResult.Failure(createAdException("TopOn splash: show callback failed: ${e.message}", e)))
                }
            }
        }
    }

    /**
     * 销毁广告
     */
    fun destroyAd() {
        synchronized(adCachePool) {
            adCachePool.clear()
        }
        AdLogger.d("TopOn开屏广告已销毁")
    }

    /**
     * 销毁控制器
     */
    fun destroy() {
        destroyAd()
        AdLogger.d("TopOn开屏广告控制器已清理")
    }
    
    /**
     * 销毁正在显示的广告
     * 在Activity销毁或广告关闭时调用，释放正在显示的开屏广告资源
     */
    fun destroyShowingAd() {
        currentShowingAd?.let { ad ->
            // TUSplashAd本身没有直接的listener设置方法，主要清理引用
            AdLogger.d("TopOn开屏广告正在显示的广告已销毁")
        }
        currentShowingAd = null
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
