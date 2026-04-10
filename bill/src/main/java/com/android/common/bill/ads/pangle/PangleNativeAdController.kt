package com.android.common.bill.ads.pangle

import android.content.Context
import android.view.ViewGroup
import com.bytedance.sdk.openadsdk.api.model.PAGErrorModel
import com.bytedance.sdk.openadsdk.api.nativeAd.PAGNativeAd
import com.bytedance.sdk.openadsdk.api.nativeAd.PAGNativeAdInteractionCallback
import com.bytedance.sdk.openadsdk.api.nativeAd.PAGNativeRequest
import com.bytedance.sdk.openadsdk.api.nativeAd.PAGNativeAdLoadCallback
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
import com.android.common.bill.ui.pangle.PangleNativeAdStyle
import com.android.common.bill.ui.pangle.PangleNativeAdView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.ceil
import kotlin.math.roundToLong

/**
 * Pangle原生广告控制器
 * 提供原生广告的加载和管理功能
 * 参考文档: https://www.pangleglobal.com/integration/android-native-ads
 * 
 * 注意：Pangle原生广告支持四种格式：
 * - 大图（1.91:1比例）
 * - 1280*720视频
 * - 方形图片
 * - 方形视频
 */
class PangleNativeAdController private constructor() {

    // 累积点击统计（持久化）
    private var totalClickCount by DataStoreIntDelegate("pangle_native_ad_total_clicks", 0)

    // 累积关闭统计（持久化）
    private var totalCloseCount by DataStoreIntDelegate("pangle_native_ad_total_close", 0)
    
    // 累积加载次数统计（持久化）
    private var totalLoadCount by DataStoreIntDelegate("pangle_native_ad_total_loads", 0)

    // 累积加载成功次数统计（持久化）
    private var totalLoadSucCount by DataStoreIntDelegate("pangle_native_ad_total_load_suc", 0)
    private var totalLoadFailCount by DataStoreIntDelegate("pangle_native_ad_total_load_fails", 0)
    
    // 累积展示失败次数统计（持久化）
    private var totalShowFailCount by DataStoreIntDelegate("pangle_native_ad_total_show_fails", 0)
    
    // 累积触发统计（持久化）
    private var totalShowTriggerCount by DataStoreIntDelegate("pangle_native_ad_total_show_triggers", 0)
    
    // 累积展示统计（持久化）
    private var totalShowCount by DataStoreIntDelegate("pangle_native_ad_total_shows", 0)
    
    // 当前正在显示的广告（用于资源释放）
    private var currentShowingAd: PAGNativeAd? = null
    
    companion object {
        private const val TAG = "PangleNativeAdController"
        private const val AD_TIMEOUT = 1 * 60 * 60 * 1000L // 1小时过期
        private const val DEFAULT_CACHE_SIZE_PER_AD_UNIT = 1
        
        @Volatile
        private var INSTANCE: PangleNativeAdController? = null
        
        fun getInstance(): PangleNativeAdController {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PangleNativeAdController().also { INSTANCE = it }
            }
        }
    }
    
    // 内存缓存池 - 存储预加载的广告
    private val adCachePool = mutableListOf<CachedNativeAd>()
    private val maxCacheSizePerAdUnit = DEFAULT_CACHE_SIZE_PER_AD_UNIT
    
    private val nativeAdView = PangleNativeAdView()
    
    /**
     * 缓存的原生广告数据类
     */
    private data class CachedNativeAd(
        val ad: PAGNativeAd,
        val adUnitId: String,
        val loadTime: Long = System.currentTimeMillis()
    ) {
        fun isExpired(): Boolean {
            return System.currentTimeMillis() - loadTime > AD_TIMEOUT || !ad.isReady
        }
    }
    
    /**
     * 预加载原生广告（可选，用于提前准备）
     * @param context 上下文
     * @param adUnitId 广告位ID，如果为空则使用默认ID
     */
    suspend fun preloadAd(context: Context, adUnitId: String? = null): AdResult<Unit> {
        val finalAdUnitId = adUnitId ?: BillConfig.pangle.nativeId
        
        // 检查缓存是否有效
        val cached = synchronized(adCachePool) {
            adCachePool.firstOrNull { it.adUnitId == finalAdUnitId && !it.isExpired() }
        }
        if (cached != null) {
            AdLogger.d("Pangle原生广告已有有效缓存，广告位ID: %s", finalAdUnitId)
            return AdResult.Success(Unit)
        }
        
        return loadAdToCache(context, finalAdUnitId)
    }
    
    /**
     * 获取原生广告（自动处理加载）
     * @param context 上下文
     * @param adUnitId 广告位ID，如果为空则使用默认ID
     */
    suspend fun getAd(context: Context, adUnitId: String? = null): AdResult<PAGNativeAd> {
        val finalAdUnitId = adUnitId ?: BillConfig.pangle.nativeId
        
        // 1. 尝试从缓存获取广告
        var cachedAd = getCachedAd(finalAdUnitId)
        
        // 2. 如果缓存为空，立即加载并缓存一个广告
        if (cachedAd == null) {
            AdLogger.d("缓存为空，立即加载Pangle原生广告，广告位ID: %s", finalAdUnitId)
            loadAdToCache(context, finalAdUnitId)
            cachedAd = getCachedAd(finalAdUnitId)
        }
        
        return if (cachedAd != null) {
            AdLogger.d("使用缓存中的Pangle原生广告，广告位ID: %s", finalAdUnitId)
            AdResult.Success(cachedAd.ad)
        } else {
            AdResult.Failure(createAdException("Pangle native: load returned null"))
        }
    }
    
    /**
     * 显示原生广告到指定容器（简化版接口）
     * @param context 上下文
     * @param container 目标容器（根视图）
     * @param style 广告样式，默认为标准样式
     * @param adUnitId 广告位ID，如果为空则使用默认ID
     * @return 是否显示成功
     */
    suspend fun showAdInContainer(
        context: Context,
        container: ViewGroup,
        style: PangleNativeAdStyle = BillConfig.pangle.nativeStyleStandard,
        adUnitId: String? = null,
        sessionId: String = ""
    ): Boolean {
        val finalAdUnitId = adUnitId ?: BillConfig.pangle.nativeId
        val showSessionId = sessionId
        val showIsPreload = peekCachedAd(finalAdUnitId) != null
        
        // 累积触发统计
        totalShowTriggerCount++
        AdLogger.d("Pangle原生广告累积触发展示次数: $totalShowTriggerCount")

        // 拦截器检查
// 注册 Activity 销毁时的清理回调
        (context as? androidx.fragment.app.FragmentActivity)?.let { activity ->
            AdDestroyManager.instance.register(activity) {
                AdLogger.d("Pangle原生广告: Activity销毁，清理展示资源")
                // 先销毁正在显示的广告对象
                destroyShowingAd()
                // 再移除已添加的广告 View
                container.removeAllViews()
            }
        }
        
        // 检查缓存过期
        synchronized(adCachePool) {
            if (adCachePool.any { it.adUnitId == finalAdUnitId && it.isExpired() }) {
                AdEventReporter.reportTimeoutCache(AdType.NATIVE, AdPlatform.PANGLE, finalAdUnitId)
            }
        }

        return try {
            when (val result = getAd(context, adUnitId)) {
                is AdResult.Success -> {
                    val nativeAd = result.data
                    
                    // 记录当前显示的广告（用于资源释放）
                    currentShowingAd = nativeAd

                    if(!nativeAd.isReady){
                        throw IllegalArgumentException("native_not_ready")
                    }
                    var currentRevenueUsd: Double? = null
                    var currentCurrency: String? = null
                    var currentAdSource: String? = null
                    var impressionPlacement: String? = null
                    var impressionRevenueAdUnit: String? = null

                    // 使用PangleNativeAdView绑定广告到容器，内部会处理 registerViewForInteraction
                    val success = nativeAdView.bindNativeAdToContainer(
                        context = context,
                        container = container,
                        nativeAd = nativeAd,
                        style = style,
                        interactionListener = object : PAGNativeAdInteractionCallback() {
                            var currentAdUniqueId = ""
                            override fun onAdShowed() {
                                AdLogger.d("Pangle原生广告开始显示")
                                val pagRevenueInfo: PAGRevenueInfo? = nativeAd.pagRevenueInfo
                                val ecpmInfo: PAGAdEcpmInfo? = pagRevenueInfo?.showEcpm
                                currentCurrency = ecpmInfo?.currency
                                currentAdSource = ecpmInfo?.adnName
                                impressionPlacement = ecpmInfo?.placement
                                impressionRevenueAdUnit = ecpmInfo?.adUnit
                                // Pangle 的 revenue 本身就是美元，直接使用
                                val revenueUsd = ecpmInfo?.revenue?.toDoubleOrNull() ?: 0.0
                                currentRevenueUsd = revenueUsd
                                val impressionValue = revenueUsd

                                totalShowCount++
                                AdLogger.d("Pangle原生广告累积展示次数: $totalShowCount")

                                currentAdUniqueId = "${java.util.UUID.randomUUID()}_${ecpmInfo?.adUnit}_${ecpmInfo?.placement}"
                                AdConfigManager.recordShow(AdType.NATIVE, AdPlatform.PANGLE)

                                AdEventReporter.reportImpression(AdType.NATIVE, AdPlatform.PANGLE, finalAdUnitId, currentAdUniqueId, totalShowCount, currentAdSource ?: AdPlatform.PANGLE.key, impressionValue, currentCurrency ?: "USD", sessionId = showSessionId, isPreload = showIsPreload)

                                currentRevenueUsd?.let { revenueValue ->
                                    AdRevenueReporter.reportRevenue(AdType.NATIVE, AdPlatform.PANGLE, impressionRevenueAdUnit ?: finalAdUnitId, revenueValue, currentCurrency ?: "USD", currentAdSource ?: AdPlatform.PANGLE.key, impressionPlacement)
                                    // Pangle 的 revenue 本身就是美元，直接使用
                                    val revenueUsd = ecpmInfo?.revenue?.toDoubleOrNull()?.toLong() ?: 0L
                                    AdLogger.d(
                                        "Pangle原生广告收益上报(onShow): adUnit=%s, placement=%s, adn=%s, revenueUsd=%.4f, currency=%s",
                                        impressionRevenueAdUnit ?: finalAdUnitId,
                                        impressionPlacement ?: "",
                                        currentAdSource ?: AdPlatform.PANGLE.key,
                                        revenueValue,
                                        currentCurrency ?: ""
                                    )
                                }
                                
                                // 异步预加载下一个广告到缓存（如果缓存未满）
                                if (!isCacheFull(finalAdUnitId)) {
                                    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                                        try {
                                            preloadAd(context, finalAdUnitId)
                                        } catch (e: Exception) {
                                            AdLogger.e("Pangle原生广告预加载失败", e)
                                        }
                                    }
                                }
                            }

                            override fun onAdClicked() {
                                AdLogger.d("Pangle原生广告被点击")
                                totalClickCount++
                                AdLogger.d("Pangle原生广告累积点击次数: $totalClickCount")

                                // 记录点击用于重复点击保护
                                AdClickProtectionController.recordClick(currentAdUniqueId)
                                AdConfigManager.recordClick(AdType.NATIVE, AdPlatform.PANGLE)

                                AdEventReporter.reportClick(AdType.NATIVE, AdPlatform.PANGLE, finalAdUnitId, currentAdUniqueId, totalClickCount, currentAdSource ?: AdPlatform.PANGLE.key, nativeAd.pagRevenueInfo?.showEcpm?.revenue?.toDoubleOrNull() ?: 0.0, currentCurrency ?: "USD")
                            }

                            override fun onAdDismissed() {
                                AdLogger.d("Pangle原生广告关闭")
                                totalCloseCount++
                                AdEventReporter.reportClose(AdType.NATIVE, AdPlatform.PANGLE, finalAdUnitId, totalCloseCount, currentAdSource ?: AdPlatform.PANGLE.key, nativeAd.pagRevenueInfo?.showEcpm?.revenue?.toDoubleOrNull() ?: 0.0, currentCurrency ?: "USD")
                            }

                            override fun onAdShowFailed(error: PAGErrorModel) {
                                super.onAdShowFailed(error)
                                totalShowFailCount++
                                AdLogger.e(
                                    "Pangle原生广告show failed: code=%d, message=%s",
                                    error.errorCode,
                                    error.errorMessage
                                )
                                AdEventReporter.reportShowFail(AdType.NATIVE, AdPlatform.PANGLE, finalAdUnitId, totalShowFailCount, error.errorMessage.orEmpty(), currentAdSource ?: AdPlatform.PANGLE.key, sessionId = showSessionId, isPreload = showIsPreload)
                            }
                        }
                    )
                    
                    if (success) {
                        true
                    } else {
                        totalShowFailCount++
                        AdLogger.d("Pangle原生广告累积展示失败次数: $totalShowFailCount")
                        AdEventReporter.reportShowFail(AdType.NATIVE, AdPlatform.PANGLE, finalAdUnitId, totalShowFailCount, "bind_failed", sessionId = showSessionId, isPreload = showIsPreload)
                        false
                    }
                }
                is AdResult.Failure -> {
                    // 累积展示失败次数统计
                    totalShowFailCount++
                    AdLogger.d("Pangle原生广告累积展示失败次数: $totalShowFailCount")
                    
                    AdEventReporter.reportShowFail(AdType.NATIVE, AdPlatform.PANGLE, finalAdUnitId, totalShowFailCount, result.error.message, sessionId = showSessionId, isPreload = showIsPreload)
                    false
                }
            }
        } catch (e: Exception) {
            // 累积展示失败次数统计
            totalShowFailCount++
            AdLogger.d("Pangle原生广告累积展示失败次数: $totalShowFailCount")
            
            AdEventReporter.reportShowFail(AdType.NATIVE, AdPlatform.PANGLE, finalAdUnitId, totalShowFailCount, "${e.message}", sessionId = showSessionId, isPreload = showIsPreload)
            
            AdLogger.e("显示Pangle原生广告失败", e)
            false
        }
    }
    
    /**
     * 基础广告加载方法
     */
    @Suppress("UNUSED_PARAMETER")
    private suspend fun loadAd(context: Context, adUnitId: String): PAGNativeAd? {
        // 累积加载次数统计
        totalLoadCount++
        AdLogger.d("Pangle原生广告累积加载次数: $totalLoadCount")
        
        val requestId = AdEventReporter.reportStartLoad(AdType.NATIVE, AdPlatform.PANGLE, adUnitId, totalLoadCount)
        
        return suspendCancellableCoroutine { continuation ->
            val startTime = System.currentTimeMillis()
            
            // 创建PAGNativeRequest对象（推荐作为Activity的成员变量）
            val request = PAGNativeRequest(context)
            
            // 加载广告并注册回调
            PAGNativeAd.loadAd(adUnitId, request, object : PAGNativeAdLoadCallback {
                override fun onAdLoaded(ad: PAGNativeAd) {
                    val loadTime = System.currentTimeMillis() - startTime
                    AdLogger.d("Pangle原生广告加载成功，广告位ID: %s, 耗时: %dms", adUnitId, loadTime)
                    totalLoadSucCount++
                    
                    AdEventReporter.reportLoaded(AdType.NATIVE, AdPlatform.PANGLE, adUnitId, totalLoadSucCount, AdPlatform.PANGLE.key, ceil(loadTime / 1000.0).toInt(), requestId)

                    continuation.resume(ad)
                }

                override fun onError(model :PAGErrorModel) {
                    val code = model.errorCode
                    val message = model.errorMessage
                    val loadTime = System.currentTimeMillis() - startTime
                    AdLogger.e("Pangle原生ad load failed，广告位ID: %s, 耗时: %dms, 错误码: %d, 错误信息: %s", 
                        adUnitId, loadTime, code, message)
                    
                    totalLoadFailCount++
                    AdEventReporter.reportLoadFail(AdType.NATIVE, AdPlatform.PANGLE, adUnitId, totalLoadFailCount, AdPlatform.PANGLE.key, ceil(loadTime / 1000.0).toInt(), message, requestId)
                    
                    continuation.resume(null)
                }
            })
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
                AdLogger.w("广告位 %s 缓存已满，当前缓存: %d/%d", adUnitId, currentAdUnitCount, maxCacheSizePerAdUnit)
                return AdResult.Success(Unit)
            }
            
            // 加载广告
            val nativeAd = loadAd(context, adUnitId)
            if (nativeAd != null) {
                synchronized(adCachePool) {
                    adCachePool.add(CachedNativeAd(nativeAd, adUnitId))
                    val currentCount = getCachedAdCount(adUnitId)
                    AdLogger.d("Pangle原生广告加载成功并缓存，广告位ID: %s，该广告位缓存数量: %d/%d", adUnitId, currentCount, maxCacheSizePerAdUnit)
                }
                AdResult.Success(Unit)
            } else {
                AdResult.Failure(createAdException("Pangle native ad load returned null"))
            }
        } catch (e: Exception) {
            AdLogger.e("Pangle原生loadAdToCache异常", e)
            AdResult.Failure(AdException(0, "Pangle native ad loadAdToCache exception: ${e.message}", e))
        }
    }
    
    /**
     * 从缓存获取广告
     */
    private fun getCachedAd(adUnitId: String): CachedNativeAd? {
        synchronized(adCachePool) {
            val index = adCachePool.indexOfFirst { it.adUnitId == adUnitId && !it.isExpired() }
            return if (index != -1) {
                adCachePool.removeAt(index)
            } else {
                null
            }
        }
    }

    private fun peekCachedAd(adUnitId: String): PAGNativeAd? {
        synchronized(adCachePool) {
            return adCachePool.firstOrNull { it.adUnitId == adUnitId && !it.isExpired() }?.ad
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
     * 获取当前加载的广告数据
     */
    fun getCurrentAd(adUnitId: String? = null): PAGNativeAd? {
        val finalAdUnitId = adUnitId ?: BillConfig.pangle.nativeId
        return peekCachedAd(finalAdUnitId)
    }
    
    /**
     * 检查是否有可用的广告
     */
    fun isAdLoaded(adUnitId: String? = null): Boolean {
        val finalAdUnitId = adUnitId ?: BillConfig.pangle.nativeId
        return getCachedAdCount(finalAdUnitId) > 0
    }
    
    /**
     * 销毁广告
     */
    fun destroyAd() {
        synchronized(adCachePool) {
            // PAGNativeAd没有destroy方法，只需要清理缓存
            adCachePool.clear()
        }
        AdLogger.d("Pangle原生广告已销毁")
    }
    
    /**
     * 销毁正在显示的广告
     * 在Activity销毁或广告关闭时调用，释放正在显示的原生广告资源
     */
    fun destroyShowingAd() {
        currentShowingAd?.let { ad ->
            ad.unregisterView()
            AdLogger.d("Pangle原生广告正在显示的广告已销毁")
        }
        currentShowingAd = null
    }
    
    /**
     * 清理资源
     */
    fun destroy() {
        destroyAd()
        AdLogger.d("Pangle原生广告控制器已清理")
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
