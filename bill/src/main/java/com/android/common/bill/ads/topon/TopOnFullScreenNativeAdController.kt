package com.android.common.bill.ads.topon

import android.content.Context
import android.view.ViewGroup
import androidx.lifecycle.LifecycleOwner
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
import com.android.common.bill.ads.util.PositionGet
import com.android.common.bill.ui.topon.ToponFullScreenNativeAdView
import net.corekit.core.ads.RevenueAdData
import net.corekit.core.ads.RevenueAdManager
import net.corekit.core.ads.RevenueInfo
import net.corekit.core.ext.DataStoreIntDelegate
import net.corekit.core.report.ReportDataManager
import com.thinkup.core.api.AdError
import com.thinkup.core.api.TUAdConst
import com.thinkup.core.api.TUAdInfo
import com.thinkup.nativead.api.NativeAd
import com.thinkup.nativead.api.TUNative
import com.thinkup.nativead.api.TUNativeNetworkListener
import com.thinkup.nativead.api.TUNativeEventListener
import com.thinkup.nativead.api.TUNativeDislikeListener
import com.thinkup.nativead.api.TUNativeAdView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.ceil

/**
 * TopOn 全屏原生广告控制器
 * 参考 AdMob 全屏原生广告控制器实现，保持埋点一致
 */
class TopOnFullScreenNativeAdController private constructor() {

    // 当前展示的sessionId和isPreload
    private var currentSessionId: String = ""
    private var currentIsPreload: Boolean = false
    @Volatile
    private var onAdDisplayedCallback: (() -> Unit)? = null

    // 累积统计（持久化）
    private var totalClickCount by DataStoreIntDelegate("topon_full_native_total_clicks", 0)
    private var totalCloseCount by DataStoreIntDelegate("topon_full_native_total_close", 0)
    private var totalLoadCount by DataStoreIntDelegate("topon_full_native_total_loads", 0)
    private var totalLoadSucCount by DataStoreIntDelegate("topon_full_native_total_load_suc", 0)
    private var totalLoadFailCount by DataStoreIntDelegate("topon_full_native_total_load_fails", 0)
    private var totalShowFailCount by DataStoreIntDelegate("topon_full_native_total_show_fails", 0)
    private var totalShowTriggerCount by DataStoreIntDelegate("topon_full_native_total_show_triggers", 0)
    private var totalShowCount by DataStoreIntDelegate("topon_full_native_total_shows", 0)

    companion object {
        private const val TAG = "TopOnFullScreenNativeAdController"
        private const val AD_TIMEOUT = 1 * 60 * 60 * 1000L // 1小时过期
        private const val DEFAULT_CACHE_SIZE_PER_AD_UNIT = 1

        @Volatile
        private var INSTANCE: TopOnFullScreenNativeAdController? = null

        fun getInstance(): TopOnFullScreenNativeAdController {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TopOnFullScreenNativeAdController().also { INSTANCE = it }
            }
        }
    }

    // 内存缓存池 - 存储预加载的广告
    private val adCachePool = mutableListOf<CachedFullScreenNativeAd>()
    private val maxCacheSizePerAdUnit = DEFAULT_CACHE_SIZE_PER_AD_UNIT

    private val fullScreenAdView = ToponFullScreenNativeAdView()

    // 当前广告的收益信息（临时存储）
    private var currentAdInfo: TUAdInfo? = null
    
    // 全屏原生广告是否正在显示的标识
    private var isShowing: Boolean = false
    
    // 当前正在显示的广告（用于资源释放）
    private var currentShowingAd: TUNative? = null

    /**
     * 缓存的全屏原生广告数据类
     */
    private data class CachedFullScreenNativeAd(
        val ad: TUNative,
        val placementId: String,
        val loadTime: Long = System.currentTimeMillis()
    ) {
        fun isExpired(): Boolean {
            return (System.currentTimeMillis() - loadTime > AD_TIMEOUT) || (ad.nativeAd == null) || (ad.nativeAd?.isValid != true)
        }
    }

    /**
     * 预加载全屏原生广告
     * @param context 上下文
     * @param placementId 广告位ID，如果为空则使用默认ID
     */
    suspend fun preloadAd(context: Context, placementId: String? = null): AdResult<Unit> {
        val finalPlacementId = placementId ?: BillConfig.topon.fullNativeId
        
        // 检查缓存是否有效
        val cached = synchronized(adCachePool) {
            adCachePool.firstOrNull { it.placementId == finalPlacementId && !it.isExpired() }
        }
        if (cached != null) {
            AdLogger.d("TopOn全屏原生广告已有有效缓存，广告位ID: %s", finalPlacementId)
            return AdResult.Success(Unit)
        }
        
        return loadAdToCache(context, finalPlacementId)
    }

    /**
     * 显示全屏原生广告到指定容器
     * @param context 上下文
     * @param container 目标容器
     * @param lifecycleOwner 生命周期所有者
     * @param placementId 广告位ID，如果为空则使用默认ID
     * @return AdResult<Unit> 广告显示结果
     */
    suspend fun showAdInContainer(
        context: Context,
        container: ViewGroup,
        lifecycleOwner: LifecycleOwner,
        placementId: String? = null,
        sessionId: String = "",
        onAdDisplayed: (() -> Unit)? = null
    ): AdResult<Unit> {
        val finalPlacementId = placementId ?: BillConfig.topon.fullNativeId

        totalShowTriggerCount++

        // 拦截器检查
// 注册 Activity 销毁时的清理回调
        (context as? androidx.fragment.app.FragmentActivity)?.let { activity ->
            AdDestroyManager.instance.register(activity) {
                AdLogger.d("TopOn全屏原生广告: Activity销毁，清理展示资源")
                // 销毁正在显示的广告对象
                destroyShowingAd()
                // 移除已添加的广告 View
                container.removeAllViews()
                isShowing = false
            }
        }

        currentSessionId = sessionId
        currentIsPreload = hasCachedAd(finalPlacementId)
        onAdDisplayedCallback = onAdDisplayed

        // 检查缓存过期
        synchronized(adCachePool) {
            if (adCachePool.any { it.placementId == finalPlacementId && it.isExpired() }) {
                AdEventReporter.reportTimeoutCache(AdType.FULL_SCREEN_NATIVE, AdPlatform.TOPON, finalPlacementId)
            }
        }

        return try {
            // 显示加载视图
            fullScreenAdView.createFullScreenLoadingView(context, container)

            when (val result = getAd(context, finalPlacementId)) {
                is AdResult.Success -> {
                    val tuNative = result.data
                    val nativeAd = tuNative.getNativeAd()
                    if (nativeAd == null) {
                        AdLogger.e("TopOn全屏原生广告获取NativeAd失败")
                        totalShowFailCount++
                        AdEventReporter.reportShowFail(AdType.FULL_SCREEN_NATIVE, AdPlatform.TOPON, finalPlacementId, totalShowFailCount, "TopOn full-screen native: ad data fetch failed", sessionId = currentSessionId, isPreload = currentIsPreload)
                        return AdResult.Failure(createAdException("TopOn full-screen native: ad data fetch failed"))
                    }

                    // 设置事件监听器
                    nativeAd.setNativeEventListener(createNativeEventListener(finalPlacementId, nativeAd, sessionId = currentSessionId, isPreload = currentIsPreload))
                    
                    // 设置关闭按钮监听器
                    nativeAd.setDislikeCallbackListener(object : TUNativeDislikeListener() {
                        override fun onAdCloseButtonClick(
                            p0: TUNativeAdView?,
                            adInfo: TUAdInfo
                        ) {
                            AdLogger.d("TopOn全屏原生广告关闭")
                            currentAdInfo = adInfo
                            totalCloseCount++

                            val revenueValue = adInfo.publisherRevenue ?: adInfo.ecpm ?: 0.0
                            val revenueCurrency = adInfo.currency ?: "USD"

                            AdEventReporter.reportClose(AdType.FULL_SCREEN_NATIVE, AdPlatform.TOPON, finalPlacementId, totalCloseCount, adInfo.networkName ?: "", revenueValue, revenueCurrency)
                        }
                    })

                    // 绑定广告到容器
                    val success = fullScreenAdView.bindFullScreenNativeAdToContainer(
                        context, container, nativeAd, lifecycleOwner
                    )

                    if (success) {
                        // 记录当前显示的广告（用于资源释放）
                        currentShowingAd = tuNative
                        AdResult.Success(Unit)
                    } else {
                        onAdDisplayedCallback = null
                        totalShowFailCount++
                        AdEventReporter.reportShowFail(AdType.FULL_SCREEN_NATIVE, AdPlatform.TOPON, finalPlacementId, totalShowFailCount, "TopOn full-screen native: ad bindView failed", sessionId = currentSessionId, isPreload = currentIsPreload)
                        AdResult.Failure(createAdException("TopOn full-screen native: ad bindView failed"))
                    }
                }
                is AdResult.Failure -> {
                    onAdDisplayedCallback = null
                    totalShowFailCount++
                    AdEventReporter.reportShowFail(AdType.FULL_SCREEN_NATIVE, AdPlatform.TOPON, finalPlacementId, totalShowFailCount, result.error.message, sessionId = currentSessionId, isPreload = currentIsPreload)
                    AdResult.Failure(result.error)
                }
            }
        } catch (e: Exception) {
            onAdDisplayedCallback = null
            AdLogger.e("显示TopOn全屏原生广告失败", e)
            totalShowFailCount++
            AdEventReporter.reportShowFail(AdType.FULL_SCREEN_NATIVE, AdPlatform.TOPON, finalPlacementId, totalShowFailCount, e.message.orEmpty(), sessionId = currentSessionId, isPreload = currentIsPreload)
            AdResult.Failure(AdException(code = -2, message = "show full-screen native ad exception: ${e.message}", cause = e))
        }
    }

    /**
     * 获取全屏原生广告（自动处理加载）
     * @param context 上下文
     * @param placementId 广告位ID，如果为空则使用默认ID
     */
    suspend fun getAd(context: Context, placementId: String? = null): AdResult<TUNative> {
        val finalPlacementId = placementId ?: BillConfig.topon.fullNativeId

        // 1. 尝试从缓存获取广告
        var cachedAd = getCachedAd(finalPlacementId)

        // 2. 如果缓存为空，立即加载并缓存一个广告
        if (cachedAd == null) {
            AdLogger.d("缓存为空，立即加载TopOn全屏原生广告，广告位ID: %s", finalPlacementId)
            when (val loadResult = loadAdToCache(context, finalPlacementId)) {
                is AdResult.Success -> cachedAd = getCachedAd(finalPlacementId)
                is AdResult.Failure -> return AdResult.Failure(createAdException("TopOnfull-screen native ad load to cache failed"))
            }
        }

        return if (cachedAd != null) {
            AdLogger.d("使用缓存中的TopOn全屏原生广告，广告位ID: %s", finalPlacementId)
            AdResult.Success(cachedAd.ad)
        } else {
            AdResult.Failure(createAdException("TopOnfull-screen native ad no cached ad available"))
        }
    }

    /**
     * 从缓存获取广告
     * 注意：由于 TopOn SDK 可能在后台异步释放广告资源，
     * 即使此处返回非空，调用方仍需检查 nativeAd 是否为 null
     */
    private fun getCachedAd(placementId: String): CachedFullScreenNativeAd? {
        synchronized(adCachePool) {
            val index = adCachePool.indexOfFirst { it.placementId == placementId && !it.isExpired() }
            if (index == -1) return null
            
            val cached = adCachePool.removeAt(index)
            // 二次检查：防止在 isExpired() 检查后、返回前 SDK 释放了广告
            return if (cached.ad.nativeAd != null) cached else null
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
     * 检查指定广告位的缓存是否已满
     */
    private fun isCacheFull(placementId: String): Boolean {
        return getCachedAdCount(placementId) >= maxCacheSizePerAdUnit
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
                        adCachePool.add(CachedFullScreenNativeAd(loadResult.data, placementId))
                        val currentCount = getCachedAdCount(placementId)
                        AdLogger.d("TopOn全屏原生广告加载成功并缓存，广告位ID: %s，该广告位缓存数量: %d/%d", placementId, currentCount, maxCacheSizePerAdUnit)
                    }
                    AdResult.Success(Unit)
                }
                is AdResult.Failure -> AdResult.Failure(createAdException("TopOnfull-screen native ad load returned non-success state"))
            }
        } catch (e: Exception) {
            AdLogger.e("TopOn全屏原生loadAdToCache异常", e)
            AdResult.Failure(AdException(0, "TopOn full-screen native ad loadAdToCache exception: ${e.message}", e))
        }
    }

    /**
     * 基础广告加载方法（可复用）
     */
    private suspend fun loadAd(context: Context, placementId: String): AdResult<TUNative> {
        // 累积加载次数统计
        totalLoadCount++
        AdLogger.d("TopOn全屏原生广告开始加载，广告位ID: %s，当前累计加载次数: %d", placementId, totalLoadCount)

        val requestId = AdEventReporter.reportStartLoad(AdType.FULL_SCREEN_NATIVE, AdPlatform.TOPON, placementId, totalLoadCount)

        return suspendCancellableCoroutine { continuation ->
            val startTime = System.currentTimeMillis()
            val applicationContext = context.applicationContext

            // 将 tuNative 定义在外部作用域，以便在回调中访问
            var tuNative: TUNative? = null

            try {
                tuNative = TUNative(applicationContext, placementId, object : TUNativeNetworkListener {
                    override fun onNativeAdLoaded() {
                        val loadTime = System.currentTimeMillis() - startTime
                        totalLoadSucCount++

                        AdLogger.d("TopOn全屏原生广告加载成功，广告位ID: %s, 耗时: %dms", placementId, loadTime)

                        AdEventReporter.reportLoaded(AdType.FULL_SCREEN_NATIVE, AdPlatform.TOPON, placementId, totalLoadSucCount, "", ceil(loadTime / 1000.0).toInt(), requestId)

                        // 直接返回 TUNative
                        tuNative?.let { continuation.resume(AdResult.Success(it)) }
                            ?: continuation.resume(AdResult.Failure(createAdException("tuNative is null")))
                    }

                    override fun onNativeAdLoadFail(adError: AdError) {
                        val loadTime = System.currentTimeMillis() - startTime
                        val errorMsg = adError.desc ?: adError.getFullErrorInfo()
                        AdLogger.e("TopOn全屏原生ad load failed，广告位ID: %s, 耗时: %dms, 错误: %s", placementId, loadTime, adError.getFullErrorInfo())

                        totalLoadFailCount++
                        AdEventReporter.reportLoadFail(AdType.FULL_SCREEN_NATIVE, AdPlatform.TOPON, placementId, totalLoadFailCount, "", ceil(loadTime / 1000.0).toInt(), errorMsg, requestId)

                        continuation.resume(AdResult.Failure(AdException(adError.code.toInt(), errorMsg)))
                    }
                })

                // 配置广告宽高（全屏）
                val displayMetrics = applicationContext.resources.displayMetrics
                val adViewWidth = displayMetrics.widthPixels
                val adViewHeight = displayMetrics.heightPixels

                val localExtra = mutableMapOf<String, Any>()
                localExtra[TUAdConst.KEY.AD_WIDTH] = adViewWidth
                localExtra[TUAdConst.KEY.AD_HEIGHT] = adViewHeight
                tuNative.setLocalExtra(localExtra)

                // 发起广告请求
                tuNative.makeAdRequest()
            } catch (e: Exception) {
                AdLogger.e("TopOn全屏原生广告load exception", e)
                totalLoadFailCount++
                AdEventReporter.reportLoadFail(AdType.FULL_SCREEN_NATIVE, AdPlatform.TOPON, placementId, totalLoadFailCount, "", 0, e.message.orEmpty(), requestId)
                if (continuation.isActive) {
                    continuation.resume(AdResult.Failure(createAdException("TopOn full-screen native ad loadAd exception: ${e.message}", e)))
                }
            }
        }
    }

    /**
     * 创建原生广告事件监听器
     */
    private fun createNativeEventListener(
        placementId: String,
        nativeAd: NativeAd,
        sessionId: String = "",
        isPreload: Boolean = false
    ): TUNativeEventListener {
        return object : TUNativeEventListener {
            var currentAdUniqueId = ""
            override fun onAdImpressed(view: TUNativeAdView, adInfo: TUAdInfo) {
                AdLogger.d("TopOn全屏原生广告展示完成")
                currentAdInfo = adInfo
                notifyAdDisplayedIfNeeded()
                
                // 设置广告正在显示标识
                isShowing = true

                // 累积展示统计
                totalShowCount++
                AdLogger.d("TopOn全屏原生广告累积展示次数: $totalShowCount")

                // 记录展示
                AdConfigManager.recordShow(AdType.FULL_SCREEN_NATIVE, AdPlatform.TOPON)

                val revenueValue = adInfo.publisherRevenue ?: adInfo.ecpm ?: 0.0
                val revenueCurrency = adInfo.currency ?: "USD"

                currentAdUniqueId = "${java.util.UUID.randomUUID()}_${adInfo.placementId}_${adInfo.adsourceId}"

                AdEventReporter.reportImpression(AdType.FULL_SCREEN_NATIVE, AdPlatform.TOPON, placementId, currentAdUniqueId, totalShowCount, adInfo.networkName ?: "", revenueValue, revenueCurrency, sessionId = sessionId, isPreload = isPreload)

                // TopOn 的 revenueValue 已经是美元，不需要转换
                val revenueUsd = revenueValue.toLong()

                AdRevenueReporter.reportRevenue(AdType.FULL_SCREEN_NATIVE, AdPlatform.TOPON, placementId, adInfo.publisherRevenue ?: adInfo.ecpm ?: 0.0, adInfo.currency ?: "USD", adInfo.networkName, adInfo.placementId ?: "")

                // 异步预加载下一个广告到缓存（如果缓存未满）
                if (!isCacheFull(placementId)) {
                    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                        try {
                            preloadAd(view.context, placementId)
                        } catch (e: Exception) {
                            AdLogger.e("TopOn全屏原生广告预加载失败", e)
                        }
                    }
                }
            }

            override fun onAdClicked(view: TUNativeAdView, adInfo: TUAdInfo) {
                AdLogger.d("TopOn全屏原生广告被点击")
                currentAdInfo = adInfo

                // 累积点击统计
                totalClickCount++

                // 记录点击用于重复点击保护
                AdClickProtectionController.recordClick(currentAdUniqueId)
                AdLogger.d("TopOn全屏原生广告累积点击次数: $totalClickCount")

                AdConfigManager.recordClick(AdType.FULL_SCREEN_NATIVE, AdPlatform.TOPON)

                val revenueValue = adInfo.publisherRevenue ?: adInfo.ecpm ?: 0.0
                val revenueCurrency = adInfo.currency ?: "USD"

                AdEventReporter.reportClick(AdType.FULL_SCREEN_NATIVE, AdPlatform.TOPON, placementId, currentAdUniqueId, totalClickCount, adInfo.networkName ?: "", revenueValue, revenueCurrency)
            }

            override fun onAdVideoStart(p0: TUNativeAdView?) {
            }

            override fun onAdVideoEnd(p0: TUNativeAdView?) {
            }

            override fun onAdVideoProgress(
                p0: TUNativeAdView?,
                p1: Int
            ) {
            }

            fun onAdClosed(view: TUNativeAdView, adInfo: TUAdInfo) {
            }
        }
    }

    private fun notifyAdDisplayedIfNeeded() {
        val callback = onAdDisplayedCallback ?: return
        onAdDisplayedCallback = null
        try {
            callback.invoke()
        } catch (e: Exception) {
            AdLogger.e("TopOn全屏原生 onAdDisplayed 回调异常", e)
        }
    }

    fun closeEvent(placementId: String = "") {
        // 设置广告不再显示标识
        isShowing = false
        onAdDisplayedCallback = null
        totalCloseCount++

        AdEventReporter.reportClose(AdType.FULL_SCREEN_NATIVE, AdPlatform.TOPON, placementId, totalCloseCount, currentAdInfo?.networkName ?: "", currentAdInfo?.publisherRevenue ?: 0.0, currentAdInfo?.currency ?: "USD")
    }

    fun peekCachedAd(placementId: String = BillConfig.topon.fullNativeId): TUNative? {
        return synchronized(adCachePool) {
            adCachePool.firstOrNull { it.placementId == placementId && !it.isExpired() }?.ad
        }
    }

    fun getCurrentAd(placementId: String? = null): TUNative? {
        val finalPlacementId = placementId ?: BillConfig.topon.fullNativeId
        return peekCachedAd(finalPlacementId)
    }

    fun hasCachedAd(placementId: String? = null): Boolean {
        synchronized(adCachePool) {
            return if (placementId != null) {
                adCachePool.any { it.placementId == placementId && !it.isExpired() }
            } else {
                adCachePool.any { !it.isExpired() }
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
        AdLogger.d("TopOn全屏原生广告已销毁")
    }

    /**
     * 销毁控制器
     */
    fun destroy() {
        destroyAd()
        AdLogger.d("TopOn全屏原生广告控制器已清理")
    }
    
    /**
     * 销毁正在显示的广告
     * 在Activity销毁或广告关闭时调用，释放正在显示的全屏原生广告资源
     */
    fun destroyShowingAd() {
        currentShowingAd?.let { ad ->
            ad.nativeAd?.setNativeEventListener(null)
            ad.nativeAd?.setDislikeCallbackListener(null)
            AdLogger.d("TopOn全屏原生广告正在显示的广告已销毁")
        }
        currentShowingAd = null
    }

    /**
     * 创建广告异常
     */
    private fun createAdException(message: String, cause: Throwable? = null): AdException {
        return AdException(
            code = -1,
            message = message,
            cause = cause
        )
    }

    /**
     * 获取全屏原生广告是否正在显示的状态
     * @return true 如果全屏原生广告正在显示，false 否则
     */
    fun isAdShowing(): Boolean {
        return isShowing
    }
}
