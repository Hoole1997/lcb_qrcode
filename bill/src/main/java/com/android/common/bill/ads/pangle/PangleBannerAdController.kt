package com.android.common.bill.ads.pangle

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.bytedance.sdk.openadsdk.api.banner.PAGBannerAd
import com.bytedance.sdk.openadsdk.api.banner.PAGBannerAdInteractionCallback
import com.bytedance.sdk.openadsdk.api.banner.PAGBannerAdLoadCallback
import com.bytedance.sdk.openadsdk.api.banner.PAGBannerRequest
import com.bytedance.sdk.openadsdk.api.banner.PAGBannerSize
import com.bytedance.sdk.openadsdk.api.model.PAGErrorModel
import com.bytedance.sdk.openadsdk.api.model.PAGAdEcpmInfo
import com.bytedance.sdk.openadsdk.api.model.PAGRevenueInfo
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
import net.corekit.core.ads.RevenueAdData
import net.corekit.core.ads.RevenueAdManager
import net.corekit.core.ads.RevenueInfo
import net.corekit.core.ext.DataStoreIntDelegate
import net.corekit.core.report.ReportDataManager
import com.android.common.bill.ads.util.AdDestroyManager
import com.android.common.bill.ads.util.PositionGet
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.ceil
import kotlin.math.roundToLong

/**
 * Pangle Banner广告控制器
 * 提供标准Banner广告显示功能
 * 参考文档: https://www.pangleglobal.com/integration/android-banner-ads-sdk
 * 
 * 注意：Pangle仅支持两种Banner尺寸：300x250(dp)和320x50(dp)
 */
class PangleBannerAdController private constructor() {
    
    // 当前展示的sessionId和isPreload
    private var currentSessionId: String = ""
    private var currentIsPreload: Boolean = false

    // 累积点击统计（持久化）
    private var totalClickCount by DataStoreIntDelegate("pangle_banner_ad_total_clicks", 0)

    // 累积关闭统计（持久化）
    private var totalCloseCount by DataStoreIntDelegate("pangle_banner_ad_total_close", 0)
    
    // 累积加载次数统计（持久化）
    private var totalLoadCount by DataStoreIntDelegate("pangle_banner_ad_total_loads", 0)

    // 累积加载成功次数统计（持久化）
    private var totalLoadSucCount by DataStoreIntDelegate("pangle_banner_ad_total_load_suc", 0)
    private var totalLoadFailCount by DataStoreIntDelegate("pangle_banner_ad_total_load_fails", 0)
    
    // 累积展示失败次数统计（持久化）
    private var totalShowFailCount by DataStoreIntDelegate("pangle_banner_ad_total_show_fails", 0)
    
    // 累积触发统计（持久化）
    private var totalShowTriggerCount by DataStoreIntDelegate("pangle_banner_ad_total_show_triggers", 0)
    
    // 累积展示统计（持久化）
    private var totalShowCount by DataStoreIntDelegate("pangle_banner_ad_total_shows", 0)
    
    companion object {
        private const val TAG = "PangleBannerAdController"
        private const val AD_TIMEOUT = 1 * 60 * 60 * 1000L // 1小时过期
        private const val DEFAULT_CACHE_SIZE_PER_AD_UNIT = 1
        
        // Pangle支持的Banner尺寸
        private const val BANNER_WIDTH_320 = 320 // 320x50标准Banner
        private const val BANNER_WIDTH_300 = 300 // 300x250矩形Banner
        
        @Volatile
        private var INSTANCE: PangleBannerAdController? = null
        
        fun getInstance(): PangleBannerAdController {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PangleBannerAdController().also { INSTANCE = it }
            }
        }
    }
    
    // 内存缓存池 - 存储预加载的广告
    private val adCachePool = mutableListOf<CachedBannerAd>()
    private val maxCacheSizePerAdUnit = DEFAULT_CACHE_SIZE_PER_AD_UNIT
    
    // 当前显示的Banner广告
    private var currentBannerAd: PAGBannerAd? = null
    
    /**
     * 缓存的Banner广告数据类
     */
    private data class CachedBannerAd(
        val ad: PAGBannerAd,
        val adUnitId: String,
        val loadTime: Long = System.currentTimeMillis()
    ) {
        fun isExpired(): Boolean {
            return System.currentTimeMillis() - loadTime > AD_TIMEOUT
        }
    }
    
    /**
     * 创建Banner尺寸对象
     * @param width Banner宽度（dp），支持320或300
     * @return PAGBannerSize对象
     */
    private fun createBannerSize(width: Int = BANNER_WIDTH_320): PAGBannerSize {
        return when (width) {
            BANNER_WIDTH_300 -> PAGBannerSize.BANNER_W_300_H_250
            else -> PAGBannerSize.BANNER_W_320_H_50
        }
    }
    
    /**
     * 预加载Banner广告
     * @param context 上下文
     * @param adUnitId 广告位ID，如果为空则使用默认ID
     * @param width Banner宽度（dp），默认320
     */
    suspend fun preloadAd(context: Context, adUnitId: String? = null, width: Int = BANNER_WIDTH_320): AdResult<Unit> {
        val finalAdUnitId = adUnitId ?: BillConfig.pangle.bannerId
        return loadAdToCache(context, finalAdUnitId, width)
    }

    /**
     * 基础广告加载方法
     */
    private suspend fun loadAd(context: Context, adUnitId: String, width: Int = BANNER_WIDTH_320): PAGBannerAd? {
        // 累积加载次数统计
        totalLoadCount++
        AdLogger.d("Pangle Banner广告累积加载次数: $totalLoadCount")
        
        val requestId = AdEventReporter.reportStartLoad(AdType.BANNER, AdPlatform.PANGLE, adUnitId, totalLoadCount)
        
        return suspendCancellableCoroutine { continuation ->
            val startTime = System.currentTimeMillis()

            // 创建PAGBannerSize对象
            val bannerSize = createBannerSize(width)
            
            // 创建PAGBannerRequest对象（推荐作为Activity的成员变量）
            val request = PAGBannerRequest(context,bannerSize)
            
            // 加载广告并注册回调
            PAGBannerAd.loadAd(adUnitId, request, object : PAGBannerAdLoadCallback {
                override fun onAdLoaded(ad: PAGBannerAd) {
                    val loadTime = System.currentTimeMillis() - startTime
                    AdLogger.d("Pangle Banner广告加载成功，广告位ID: %s, 耗时: %dms", adUnitId, loadTime)
                    totalLoadSucCount++
                    
                    AdEventReporter.reportLoaded(AdType.BANNER, AdPlatform.PANGLE, adUnitId, totalLoadSucCount, AdPlatform.PANGLE.key, ceil(loadTime / 1000.0).toInt(), requestId)

                    continuation.resume(ad)
                }

                override fun onError(model :PAGErrorModel) {
                    val code = model.errorCode
                    val message = model.errorMessage
                    val loadTime = System.currentTimeMillis() - startTime
                    AdLogger.e("Pangle Bannerad load failed，广告位ID: %s, 耗时: %dms, 错误码: %d, 错误信息: %s", 
                        adUnitId, loadTime, code, message)
                    
                    totalLoadFailCount++
                    AdEventReporter.reportLoadFail(AdType.BANNER, AdPlatform.PANGLE, adUnitId, totalLoadFailCount, AdPlatform.PANGLE.key, ceil(loadTime / 1000.0).toInt(), message, requestId)
                    
                    continuation.resume(null)
                }
            })
        }
    }
    
    /**
     * 加载广告到缓存
     */
    private suspend fun loadAdToCache(context: Context, adUnitId: String, width: Int = BANNER_WIDTH_320): AdResult<Unit> {
        return try {
            // 检查缓存是否已满
            val currentAdUnitCount = getCachedAdCount(adUnitId)
            if (currentAdUnitCount >= maxCacheSizePerAdUnit) {
                AdLogger.w("广告位 %s 缓存已满，当前缓存: %d/%d", adUnitId, currentAdUnitCount, maxCacheSizePerAdUnit)
                return AdResult.Success(Unit)
            }
            
            // 加载广告
            val bannerAd = loadAd(context, adUnitId, width)
            if (bannerAd != null) {
                synchronized(adCachePool) {
                    adCachePool.add(CachedBannerAd(bannerAd, adUnitId))
                    val currentCount = getCachedAdCount(adUnitId)
                    AdLogger.d("Pangle Banner广告加载成功并缓存，广告位ID: %s，该广告位缓存数量: %d/%d", adUnitId, currentCount, maxCacheSizePerAdUnit)
                }
                AdResult.Success(Unit)
            } else {
                AdResult.Failure(createAdException("Pangle banner ad load returned null"))
            }
        } catch (e: Exception) {
            AdLogger.e("Pangle Banner loadAdToCache异常", e)
            AdResult.Failure(AdException(0, "Pangle banner ad loadAdToCache exception: ${e.message}", e))
        }
    }
    
    /**
     * 显示Banner广告（自动处理加载）
     * @param context 上下文
     * @param container 目标容器
     * @param adUnitId 广告位ID，如果为空则使用默认ID
     * @param width Banner宽度（dp），默认320
     */
    suspend fun showAd(
        context: Context, 
        container: ViewGroup, 
        adUnitId: String? = null,
        width: Int = BANNER_WIDTH_320,
        sessionId: String = ""
    ): AdResult<View> {
        val finalAdUnitId = adUnitId ?: BillConfig.pangle.bannerId
        
        // 累积触发统计
        totalShowTriggerCount++
        AdLogger.d("Pangle Banner广告累积触发展示次数: $totalShowTriggerCount")
        
        // reportAdData(
        //     eventName = "ad_position",
        //     params = mapOf(
        //         "ad_unit_name" to finalAdUnitId,
        //         "position" to PositionGet.get(),
        //         "number" to totalShowTriggerCount
        //     )
        // )
        
        // 拦截器检查
// 注册 Activity 销毁时的清理回调
        (context as? androidx.fragment.app.FragmentActivity)?.let { activity ->
            AdDestroyManager.instance.register(activity) {
                AdLogger.d("Pangle Banner广告: Activity销毁，清理展示资源")
                // 先销毁正在显示的广告对象
                destroyShowingAd()
                // 再移除已添加的广告 View
                container.removeAllViews()
            }
        }
        
        currentSessionId = sessionId
        currentIsPreload = getCachedAdCount(finalAdUnitId) > 0

        // 检查缓存过期
        synchronized(adCachePool) {
            if (adCachePool.any { it.adUnitId == finalAdUnitId && it.isExpired() }) {
                AdEventReporter.reportTimeoutCache(AdType.BANNER, AdPlatform.PANGLE, finalAdUnitId)
            }
        }

        return try {
            // 1. 尝试从缓存获取广告
            var cachedAd = getCachedAd(finalAdUnitId)
            if (cachedAd == null) {
                AdLogger.d("缓存为空，立即加载Pangle Banner广告，广告位ID: %s", finalAdUnitId)
                loadAdToCache(context, finalAdUnitId, width)
                cachedAd = getCachedAd(finalAdUnitId)
            }
            
            if (cachedAd != null) {
                if(!cachedAd.ad.isReady){
                    throw IllegalArgumentException("banner_not_ready")
                }
                AdLogger.d("使用缓存中的Pangle Banner广告，广告位ID: %s", finalAdUnitId)
                // 2. 获取Banner View并添加到容器
                val bannerView = cachedAd.ad.getBannerView()
                if (bannerView != null) {
                    // 清空容器
                    container.removeAllViews()
                    
                    // 注册广告事件回调（需要在显示前注册）
                    val bannerAd = cachedAd.ad
                    var currentRevenueUsd: Double? = null
                    var currentCurrency: String? = null
                    var currentAdSource: String? = null
                    var currentPlacement: String? = null
                    var currentRevenueAdUnit: String? = null

                    bannerAd.setAdInteractionListener(object : PAGBannerAdInteractionCallback() {
                        var currentAdUniqueId = ""
                        override fun onAdShowed() {
                            AdLogger.d("Pangle Banner广告开始显示")
                            val pagRevenueInfo: PAGRevenueInfo? = bannerAd.pagRevenueInfo
                            val ecpmInfo: PAGAdEcpmInfo? = pagRevenueInfo?.showEcpm
                            currentCurrency = ecpmInfo?.currency
                            currentAdSource = ecpmInfo?.adnName
                            currentPlacement = ecpmInfo?.placement
                            currentRevenueAdUnit = ecpmInfo?.adUnit
                            // Pangle 的 revenue 本身就是美元，直接使用
                            val revenueUsd = ecpmInfo?.revenue?.toDoubleOrNull() ?: 0.0
                            currentRevenueUsd = revenueUsd
                            val impressionValue = revenueUsd

                            // 累积展示统计
                            totalShowCount++
                            AdLogger.d("Pangle Banner广告累积展示次数: $totalShowCount")

                            currentAdUniqueId = "${java.util.UUID.randomUUID()}_${ecpmInfo?.adUnit}_${ecpmInfo?.placement}"
                            AdConfigManager.recordShow(AdType.BANNER, AdPlatform.PANGLE)

                            // 上报展示事件
                            AdEventReporter.reportImpression(AdType.BANNER, AdPlatform.PANGLE, finalAdUnitId, currentAdUniqueId, totalShowCount, currentAdSource ?: AdPlatform.PANGLE.key, impressionValue, currentCurrency ?: "USD", sessionId = currentSessionId, isPreload = currentIsPreload)

                            currentRevenueUsd?.let { revenueValue ->
                                AdRevenueReporter.reportRevenue(AdType.BANNER, AdPlatform.PANGLE, currentRevenueAdUnit ?: finalAdUnitId, revenueValue, currentCurrency ?: "USD", currentAdSource ?: AdPlatform.PANGLE.key, currentPlacement)
                                // Pangle 的 revenue 本身就是美元，直接使用
                                val revenueUsd = ecpmInfo?.revenue?.toDoubleOrNull()?.toLong() ?: 0L
                                AdLogger.d(
                                    "Pangle Banner广告收益上报(onShow): adUnit=%s, placement=%s, adn=%s, revenueUsd=%.4f, currency=%s",
                                    currentRevenueAdUnit ?: finalAdUnitId,
                                    currentPlacement ?: "",
                                    currentAdSource ?: AdPlatform.PANGLE.key,
                                    revenueValue,
                                    currentCurrency ?: ""
                                )
                            }
                        }

                        override fun onAdClicked() {
                            AdLogger.d("Pangle Banner广告被点击")
                            
                            // 累积点击统计
                            totalClickCount++

                            // 记录点击用于重复点击保护
                            AdClickProtectionController.recordClick(currentAdUniqueId)
                            AdLogger.d("Pangle Banner广告累积点击次数: $totalClickCount")
                            
                            AdConfigManager.recordClick(AdType.BANNER, AdPlatform.PANGLE)
                            
                            AdEventReporter.reportClick(AdType.BANNER, AdPlatform.PANGLE, finalAdUnitId, currentAdUniqueId, totalClickCount, currentAdSource ?: AdPlatform.PANGLE.key, bannerAd.pagRevenueInfo?.showEcpm?.revenue?.toDoubleOrNull() ?: 0.0, currentCurrency ?: "USD")
                        }

                        override fun onAdDismissed() {
                            AdLogger.d("Pangle Banner广告关闭")
                            
                            totalCloseCount++
                            
                            AdEventReporter.reportClose(AdType.BANNER, AdPlatform.PANGLE, finalAdUnitId, totalCloseCount, currentAdSource ?: AdPlatform.PANGLE.key, bannerAd.pagRevenueInfo?.showEcpm?.revenue?.toDoubleOrNull() ?: 0.0, currentCurrency ?: "USD")
                        }

                        override fun onAdShowFailed(model: PAGErrorModel) {
                            super.onAdShowFailed(model)
                            totalShowFailCount++
                            AdLogger.e(
                                "Pangle Banner广告show failed: code=%d, message=%s",
                                model.errorCode,
                                model.errorMessage
                            )
                            AdEventReporter.reportShowFail(AdType.BANNER, AdPlatform.PANGLE, finalAdUnitId, totalShowFailCount, model.errorMessage.orEmpty(), currentAdSource ?: AdPlatform.PANGLE.key, sessionId = currentSessionId, isPreload = currentIsPreload)
                        }
                    })
                    
                    // 添加到容器，设置居中布局
                    val layoutParams = when (container) {
                        is FrameLayout -> {
                            FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            ).apply {
                                gravity = Gravity.CENTER
                            }
                        }
                        else -> {
                            ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            )
                        }
                    }
                    bannerView.layoutParams = layoutParams
                    container.addView(bannerView)
                    
                    // 保存当前广告引用
                    currentBannerAd = bannerAd
                    
                    AdResult.Success(bannerView)
                } else {
                    totalShowFailCount++
                    AdEventReporter.reportShowFail(AdType.BANNER, AdPlatform.PANGLE, finalAdUnitId, totalShowFailCount, "Pangle banner: banner view is null", sessionId = currentSessionId, isPreload = currentIsPreload)
                    AdResult.Failure(createAdException("Pangle banner: banner view is null"))
                }
            } else {
                // 累积展示失败次数统计
                totalShowFailCount++
                AdLogger.d("Pangle Banner广告累积展示失败次数: $totalShowFailCount")

                AdEventReporter.reportShowFail(AdType.BANNER, AdPlatform.PANGLE, finalAdUnitId, totalShowFailCount, "No fill", sessionId = currentSessionId, isPreload = currentIsPreload)

                AdResult.Failure(createAdException("Pangle banner ad no cached ad available"))
            }
        } catch (e: Exception) {
            totalShowFailCount++
            AdEventReporter.reportShowFail(AdType.BANNER, AdPlatform.PANGLE, finalAdUnitId, totalShowFailCount, e.message.orEmpty(), AdPlatform.PANGLE.key, sessionId = currentSessionId, isPreload = currentIsPreload)
            AdLogger.e("显示Pangle Banner广告失败", e)
            container.removeAllViews()
            AdResult.Failure(
                AdException(
                    code = -1,
                    message = "show Pangle banner ad exception: ${e.message}",
                    cause = e
                )
            )
        }
    }
    
    /**
     * 从缓存获取广告
     */
    private fun getCachedAd(adUnitId: String): CachedBannerAd? {
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
     * 获取当前Banner广告View
     */
    fun getCurrentBannerView(): View? {
        return currentBannerAd?.getBannerView()
    }

    fun getCurrentAd(): PAGBannerAd? = currentBannerAd
    
    /**
     * 检查是否有可用的广告
     */
    fun isAdLoaded(): Boolean {
        return currentBannerAd != null
    }
    
    /**
     * 销毁广告
     */
    fun destroyAd() {
        synchronized(adCachePool) {
            adCachePool.forEach { it.ad.destroy() }
            adCachePool.clear()
        }
        currentBannerAd?.destroy()
        currentBannerAd = null
        AdLogger.d("Pangle Banner广告已销毁")
    }
    
    /**
     * 销毁正在显示的广告
     * 在Activity销毁或广告关闭时调用，释放正在显示的Banner广告资源
     */
    fun destroyShowingAd() {
        currentBannerAd?.let { ad ->
            ad.setAdInteractionListener(null)
            ad.destroy()
            AdLogger.d("Pangle Banner广告正在显示的广告已销毁")
        }
        currentBannerAd = null
    }
    
    /**
     * 销毁控制器
     */
    fun destroy() {
        destroyAd()
        AdLogger.d("Pangle Banner广告控制器已清理")
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
