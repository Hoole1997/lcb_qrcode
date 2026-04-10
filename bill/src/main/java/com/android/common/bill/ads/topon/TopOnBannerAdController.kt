package com.android.common.bill.ads.topon

import android.app.Activity
import android.content.Context
import android.view.ViewGroup
import com.android.common.bill.BillConfig
import com.android.common.bill.ads.AdException
import com.android.common.bill.ads.AdResult
import com.android.common.bill.ads.config.AdConfigManager
import com.android.common.bill.ads.config.AdPlatform
import com.android.common.bill.ads.config.AdType
import com.android.common.bill.ads.log.AdLogger
import com.android.common.bill.ads.tracker.AdEventReporter
import com.android.common.bill.ads.tracker.AdRevenueReporter
import com.android.common.bill.ads.util.AdDestroyManager
import com.android.common.bill.ads.util.PositionGet
import com.android.common.bill.ui.topon.ToponBannerAdView
import net.corekit.core.ads.RevenueAdData
import net.corekit.core.ads.RevenueAdManager
import net.corekit.core.ads.RevenueInfo
import net.corekit.core.ext.DataStoreIntDelegate
import net.corekit.core.report.ReportDataManager
import com.android.common.bill.ads.protection.AdClickProtectionController
import com.thinkup.core.api.AdError
import com.thinkup.core.api.TUAdConst
import com.thinkup.core.api.TUAdInfo
import com.thinkup.banner.api.TUBannerView
import com.thinkup.banner.api.TUBannerListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.ceil

/**
 * TopOn Banner 广告控制器
 * 参考 AdMob Banner 广告控制器实现，保持埋点一致
 * 参考文档: https://help.toponad.net/cn/docs/heng-fu-guang-gao
 */
class TopOnBannerAdController private constructor() {

    // 当前展示的sessionId和isPreload
    private var currentSessionId: String = ""
    private var currentIsPreload: Boolean = false

    // 累积统计（持久化）
    private var totalClickCount by DataStoreIntDelegate("topon_banner_total_clicks", 0)
    private var totalCloseCount by DataStoreIntDelegate("topon_banner_total_close", 0)
    private var totalLoadCount by DataStoreIntDelegate("topon_banner_total_loads", 0)
    private var totalLoadSucCount by DataStoreIntDelegate("topon_banner_total_load_suc", 0)
    private var totalLoadFailCount by DataStoreIntDelegate("topon_banner_total_load_fails", 0)
    private var totalShowFailCount by DataStoreIntDelegate("topon_banner_total_show_fails", 0)
    private var totalShowTriggerCount by DataStoreIntDelegate("topon_banner_total_show_triggers", 0)
    private var totalShowCount by DataStoreIntDelegate("topon_banner_total_shows", 0)

    companion object {
        private const val TAG = "TopOnBannerAdController"
        private const val AD_TIMEOUT = 1 * 60 * 60 * 1000L // 1小时过期
        private const val DEFAULT_CACHE_SIZE_PER_AD_UNIT = 1
        private const val BANNER_WIDTH_320 = 320 // 标准 Banner 宽度（dp）

        @Volatile
        private var INSTANCE: TopOnBannerAdController? = null

        fun getInstance(): TopOnBannerAdController {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TopOnBannerAdController().also { INSTANCE = it }
            }
        }
    }

    // 内存缓存池 - 存储预加载的广告
    private val adCachePool = mutableListOf<CachedBannerAd>()
    private val maxCacheSizePerAdUnit = DEFAULT_CACHE_SIZE_PER_AD_UNIT

    private val bannerView = ToponBannerAdView()

    // 当前广告的收益信息（临时存储）
    private var currentAdInfo: TUAdInfo? = null
    
    // 当前正在显示的 Banner 广告（用于资源释放）
    private var currentBannerAd: TUBannerView? = null

    /**
     * 缓存的 Banner 广告数据类
     */
    private data class CachedBannerAd(
        val bannerView: TUBannerView,
        val placementId: String,
        val loadTime: Long = System.currentTimeMillis()
    ) {
        fun isExpired(): Boolean {
            return System.currentTimeMillis() - loadTime > AD_TIMEOUT
        }
    }

    /**
     * 创建 Banner 广告视图
     * @param activity Activity 上下文
     * @param placementId 广告位ID，如果为空则使用默认ID
     */
    fun createBannerAdView(activity: Activity, placementId: String? = null): TUBannerView {
        val finalPlacementId = placementId ?: BillConfig.topon.bannerId
        return TUBannerView(activity).apply {
            setPlacementId(finalPlacementId)
        }
    }

    /**
     * 预加载 Banner 广告
     * @param activity Activity 上下文
     * @param placementId 广告位ID，如果为空则使用默认ID
     */
    suspend fun preloadAd(activity: Activity, placementId: String? = null): AdResult<Unit> {
        val finalPlacementId = placementId ?: BillConfig.topon.bannerId
        return loadAdToCache(activity, finalPlacementId)
    }

    /**
     * 显示 Banner 广告（自动处理加载）
     * @param activity Activity 上下文
     * @param container 目标容器
     * @param placementId 广告位ID，如果为空则使用默认ID
     */
    suspend fun showAd(
        activity: Activity,
        container: ViewGroup,
        placementId: String? = null,
        sessionId: String = ""
    ): AdResult<TUBannerView> {
        val finalPlacementId = placementId ?: BillConfig.topon.bannerId

        // 累积触发统计
        totalShowTriggerCount++
        AdLogger.d("TopOn Banner 广告累积触发展示次数: $totalShowTriggerCount")

        // reportAdData(
        //     eventName = "ad_position",
        //     params = mapOf(
        //         "ad_unit_name" to finalPlacementId,
        //         "position" to PositionGet.get(),
        //         "number" to totalShowTriggerCount
        //     )
        // )

        // 拦截器检查
// 注册 Activity 销毁时的清理回调
        AdDestroyManager.instance.register(activity as? androidx.fragment.app.FragmentActivity ?: return AdResult.Failure(createAdException("TopOn banner: unsupported Activity type"))) {
            AdLogger.d("TopOn Banner 广告: Activity销毁，清理展示资源")
            // 销毁正在显示的广告对象
            destroyShowingAd()
            // 移除已添加的广告 View
            container.removeAllViews()
        }

        currentSessionId = sessionId
        currentIsPreload = getCachedAdCount(finalPlacementId) > 0

        // 检查缓存过期
        synchronized(adCachePool) {
            if (adCachePool.any { it.placementId == finalPlacementId && it.isExpired() }) {
                AdEventReporter.reportTimeoutCache(AdType.BANNER, AdPlatform.TOPON, finalPlacementId)
            }
        }

        return try {
            // 1. 尝试从缓存获取广告
            var cachedAd = getCachedAd(finalPlacementId)
            if (cachedAd == null) {
                AdLogger.d("缓存为空，立即加载 TopOn Banner 广告，广告位ID: %s", finalPlacementId)
                when (val loadResult = loadAdToCache(activity, finalPlacementId)) {
                    is AdResult.Success -> cachedAd = getCachedAd(finalPlacementId)
                    is AdResult.Failure -> {
                        totalShowFailCount++
                        AdEventReporter.reportShowFail(AdType.BANNER, AdPlatform.TOPON, finalPlacementId, totalShowFailCount, loadResult.error.message, sessionId = currentSessionId, isPreload = currentIsPreload)
                        return loadResult
                    }
                }
            }

            if (cachedAd != null) {
                AdLogger.d("使用缓存中的 TopOn Banner 广告，广告位ID: %s", finalPlacementId)

                // 绑定广告到容器
                val success = bannerView.bindBannerAdToContainer(
                    activity, container, cachedAd.bannerView, null
                )

                if (success) {
                    // 记录当前显示的 Banner 广告（用于资源释放）
                    currentBannerAd = cachedAd.bannerView
                    // recordShow 已移到 onBannerShow 回调中
                    AdResult.Success(cachedAd.bannerView)
                } else {
                    totalShowFailCount++
                    AdEventReporter.reportShowFail(AdType.BANNER, AdPlatform.TOPON, finalPlacementId, totalShowFailCount, "TopOn banner: ad bindView failed", sessionId = currentSessionId, isPreload = currentIsPreload)
                    AdResult.Failure(createAdException("TopOn banner: ad bindView failed"))
                }
            } else {
                totalShowFailCount++
                AdEventReporter.reportShowFail(AdType.BANNER, AdPlatform.TOPON, finalPlacementId, totalShowFailCount, "No fill", sessionId = currentSessionId, isPreload = currentIsPreload)
                AdResult.Failure(createAdException("TopOn banner ad no cached ad available"))
            }
        } catch (e: Exception) {
            totalShowFailCount++
            AdEventReporter.reportShowFail(AdType.BANNER, AdPlatform.TOPON, finalPlacementId, totalShowFailCount, e.message.orEmpty(), sessionId = currentSessionId, isPreload = currentIsPreload)
            AdLogger.e("显示 TopOn Banner 广告失败", e)
            container.removeAllViews()
            AdResult.Failure(
                AdException(
                    code = -1,
                    message = "show banner ad exception: ${e.message}",
                    cause = e
                )
            )
        }
    }

    /**
     * 从缓存获取广告
     */
    private fun getCachedAd(placementId: String): CachedBannerAd? {
        synchronized(adCachePool) {
            val index = adCachePool.indexOfFirst { it.placementId == placementId && !it.isExpired() }
            return if (index != -1) {
                adCachePool.removeAt(index)
            } else {
                null
            }
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
     * Banner 可能在同一个 show session 内触发多次展示（例如自动刷新）。
     * 只有首次展示继续复用外层 session_id，后续展示降级为普通 impression。
     */
    private fun resolveBannerImpressionSessionId(): String {
        val sessionId = currentSessionId
        if (sessionId.isBlank()) return ""
        return if (AdEventReporter.isSessionTerminal(sessionId)) {
            AdLogger.d("TopOn Banner 后续展示使用无 session 上报: sessionId=%s", sessionId)
            ""
        } else {
            sessionId
        }
    }

    /**
     * 加载广告到缓存
     */
    suspend fun loadAdToCache(activity: Activity, placementId: String): AdResult<Unit> {
        return try {
            // 检查缓存是否已满
            val currentPlacementCount = getCachedAdCount(placementId)
            if (currentPlacementCount >= maxCacheSizePerAdUnit) {
                AdLogger.w("广告位 %s 缓存已满，当前缓存: %d/%d", placementId, currentPlacementCount, maxCacheSizePerAdUnit)
                return AdResult.Success(Unit)
            }

            // 加载广告
            when (val loadResult = loadAd(activity, placementId)) {
                is AdResult.Success -> {
                    synchronized(adCachePool) {
                        adCachePool.add(CachedBannerAd(loadResult.data, placementId))
                        val currentCount = getCachedAdCount(placementId)
                        AdLogger.d("TopOn Banner 广告加载成功并缓存，广告位ID: %s，该广告位缓存数量: %d/%d", placementId, currentCount, maxCacheSizePerAdUnit)
                    }
                    AdResult.Success(Unit)
                }
                is AdResult.Failure -> AdResult.Failure(createAdException("TopOn banner ad load returned non-success state"))
            }
        } catch (e: Exception) {
            AdLogger.e("TopOn Banner loadAdToCache异常", e)
            AdResult.Failure(AdException(0, "TopOn banner ad loadAdToCache exception: ${e.message}", e))
        }
    }

    /**
     * 基础广告加载方法
     */
    private suspend fun loadAd(activity: Activity, placementId: String): AdResult<TUBannerView> {
        // 累积加载次数统计
        totalLoadCount++
        AdLogger.d("TopOn Banner 广告开始加载，广告位ID: %s，当前累计加载次数: %d", placementId, totalLoadCount)

        val requestId = AdEventReporter.reportStartLoad(AdType.BANNER, AdPlatform.TOPON, placementId, totalLoadCount)

        return suspendCancellableCoroutine { continuation ->
            val startTime = System.currentTimeMillis()

            try {
                val bannerView = TUBannerView(activity)
                bannerView.setPlacementId(placementId)

                val displayMetrics = activity.resources.displayMetrics
                val adWidth = displayMetrics.widthPixels
                val adHeight = (60 * displayMetrics.density).toInt()

                bannerView.layoutParams = ViewGroup.LayoutParams(adWidth, adHeight)

                // 设置监听器
                bannerView.setBannerAdListener(object : TUBannerListener {
                    var currentAdUniqueId = ""
                    override fun onBannerLoaded() {
                        val loadTime = System.currentTimeMillis() - startTime
                        totalLoadSucCount++

                        AdLogger.d("TopOn Banner 广告加载成功，广告位ID: %s, 耗时: %dms", placementId, loadTime)

                        AdEventReporter.reportLoaded(AdType.BANNER, AdPlatform.TOPON, placementId, totalLoadSucCount, "", ceil(loadTime / 1000.0).toInt(), requestId)

                        continuation.resume(AdResult.Success(bannerView))
                    }

                    override fun onBannerFailed(adError: AdError) {
                        val loadTime = System.currentTimeMillis() - startTime
                        val errorMsg = adError.desc ?: adError.getFullErrorInfo()
                        AdLogger.e("TopOn Banner ad load failed，广告位ID: %s, 耗时: %dms, 错误: %s", placementId, loadTime, adError.getFullErrorInfo())

                        totalLoadFailCount++
                        AdEventReporter.reportLoadFail(AdType.BANNER, AdPlatform.TOPON, placementId, totalLoadFailCount, "", ceil(loadTime / 1000.0).toInt(), errorMsg, requestId)

                        continuation.resume(AdResult.Failure(AdException(adError.code.toInt(), errorMsg)))
                    }

                    override fun onBannerClicked(adInfo: TUAdInfo) {
                        AdLogger.d("TopOn Banner 广告被点击")
                        currentAdInfo = adInfo

                        // 累积点击统计
                        totalClickCount++
                        AdLogger.d("TopOn Banner 广告累积点击次数: $totalClickCount")
                        AdClickProtectionController.recordClick(currentAdUniqueId)
                        AdConfigManager.recordClick(AdType.BANNER, AdPlatform.TOPON)

                        val revenueValue = adInfo.publisherRevenue ?: adInfo.ecpm ?: 0.0
                        val revenueCurrency = adInfo.currency ?: "USD"

                        AdEventReporter.reportClick(AdType.BANNER, AdPlatform.TOPON, placementId, currentAdUniqueId, totalClickCount, adInfo.networkName ?: "", revenueValue, revenueCurrency)
                    }

                    override fun onBannerShow(adInfo: TUAdInfo) {
                        AdLogger.d("TopOn Banner 广告展示完成")
                        currentAdInfo = adInfo

                        // 累积展示统计
                        totalShowCount++
                        AdLogger.d("TopOn Banner 广告累积展示次数: $totalShowCount")

                        // 记录展示（在SDK回调中）
                        AdConfigManager.recordShow(AdType.BANNER, AdPlatform.TOPON)

                        val revenueValue = adInfo.publisherRevenue ?: adInfo.ecpm ?: 0.0
                        val revenueCurrency = adInfo.currency ?: "USD"

                        currentAdUniqueId = "${java.util.UUID.randomUUID()}_${adInfo.placementId}_${adInfo.adsourceId}"
                        val impressionSessionId = resolveBannerImpressionSessionId()

                        AdEventReporter.reportImpression(AdType.BANNER, AdPlatform.TOPON, placementId, currentAdUniqueId, totalShowCount, adInfo.networkName ?: "", revenueValue, revenueCurrency, sessionId = impressionSessionId, isPreload = currentIsPreload)

                        // TopOn 的 revenueValue 已经是美元，不需要转换
                        val revenueUsd = revenueValue.toLong()

                        AdRevenueReporter.reportRevenue(AdType.BANNER, AdPlatform.TOPON, placementId, adInfo.publisherRevenue ?: adInfo.ecpm ?: 0.0, adInfo.currency ?: "USD", adInfo.networkName, adInfo.placementId ?: "")
                    }

                    override fun onBannerClose(adInfo: TUAdInfo) {
                        AdLogger.d("TopOn Banner 广告关闭")
                        currentAdInfo = adInfo
                        totalCloseCount++

                        val revenueValue = adInfo.publisherRevenue ?: adInfo.ecpm ?: 0.0
                        val revenueCurrency = adInfo.currency ?: "USD"

                        AdEventReporter.reportClose(AdType.BANNER, AdPlatform.TOPON, placementId, totalCloseCount, adInfo.networkName ?: "", revenueValue, revenueCurrency)
                    }

                    override fun onBannerAutoRefreshed(adInfo: TUAdInfo) {
                        AdLogger.d("TopOn Banner 广告自动刷新")
                        currentAdInfo = adInfo
                    }

                    override fun onBannerAutoRefreshFail(adError: AdError) {
                        AdLogger.e("TopOn Banner 广告自动刷新失败: %s", adError.getFullErrorInfo())
                    }
                })

                val localExtra = mutableMapOf<String, Any>()
                localExtra[TUAdConst.KEY.AD_WIDTH] = adWidth
                localExtra[TUAdConst.KEY.AD_HEIGHT] = adHeight
                bannerView.setLocalExtra(localExtra)

                // 加载广告
                bannerView.loadAd()
            } catch (e: Exception) {
                AdLogger.e("TopOn Banner 广告load exception", e)
                totalLoadFailCount++
                AdEventReporter.reportLoadFail(AdType.BANNER, AdPlatform.TOPON, placementId, totalLoadFailCount, "", 0, e.message.orEmpty(), requestId)
                if (continuation.isActive) {
                    continuation.resume(AdResult.Failure(createAdException("TopOn banner ad loadAd exception: ${e.message}", e)))
                }
            }
        }
    }

    fun peekCachedAd(placementId: String = BillConfig.topon.bannerId): TUBannerView? {
        return synchronized(adCachePool) {
            adCachePool.firstOrNull { it.placementId == placementId && !it.isExpired() }?.bannerView
        }
    }

    fun getCurrentAd(placementId: String? = null): TUBannerView? {
        val finalPlacementId = placementId ?: BillConfig.topon.bannerId
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
            adCachePool.forEach { cachedAd -> cachedAd.bannerView.destroy() }
            adCachePool.clear()
        }
        AdLogger.d("TopOn Banner 广告已销毁")
    }

    /**
     * 销毁控制器
     */
    fun destroy() {
        destroyAd()
        AdLogger.d("TopOn Banner 广告控制器已清理")
    }
    
    /**
     * 销毁正在显示的广告
     * 在Activity销毁或广告关闭时调用，释放正在显示的 Banner 广告资源
     */
    fun destroyShowingAd() {
        currentBannerAd?.let { bannerView ->
            bannerView.destroy()
            AdLogger.d("TopOn Banner 广告正在显示的广告已销毁")
        }
        currentBannerAd = null
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
}
