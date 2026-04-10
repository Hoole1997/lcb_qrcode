package com.android.common.bill.ads.admob

import android.content.Context
import android.content.res.Resources
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentActivity
import com.blankj.utilcode.util.ScreenUtils
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdValue
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.OnPaidEventListener
import com.android.common.bill.BillConfig
import com.android.common.bill.ads.AdException
import com.android.common.bill.ads.AdResult
import com.android.common.bill.ads.config.AdPlatform
import com.android.common.bill.ads.config.AdType
import com.android.common.bill.ads.config.AdConfigManager
import com.android.common.bill.ads.log.AdLogger
import com.android.common.bill.ads.protection.AdClickProtectionController
import com.android.common.bill.ads.tracker.AdEventReporter
import com.android.common.bill.ads.tracker.AdRevenueReporter
import com.android.common.bill.ads.util.AdDestroyManager
import com.android.common.bill.ads.util.runAdmobLoadOnMainThread
import net.corekit.core.ext.DataStoreIntDelegate
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.IdentityHashMap
import kotlin.coroutines.resume
import kotlin.math.ceil

/**
 * Banner广告控制器
 * 提供标准Banner广告显示功能
 */
class AdmobBannerAdController private constructor() {

    // 累积点击统计（持久化）
    private var totalClickCount by DataStoreIntDelegate("banner_ad_total_clicks", 0)

    // 累积关闭统计（持久化）
    private var totalCloseCount by DataStoreIntDelegate("banner_ad_total_close", 0)

    // 累积加载次数统计（持久化）
    private var totalLoadCount by DataStoreIntDelegate("banner_ad_total_loads", 0)

    // 累积加载成功次数统计（持久化）
    private var totalLoadSucCount by DataStoreIntDelegate("banner_ad_total_load_suc", 0)

    // 累积加载失败次数统计（持久化）
    private var totalLoadFailCount by DataStoreIntDelegate("banner_ad_total_load_fails", 0)

    // 累积展示失败次数统计（持久化）
    private var totalShowFailCount by DataStoreIntDelegate("banner_ad_total_show_fails", 0)

    // 累积触发统计（持久化）
    private var totalShowTriggerCount by DataStoreIntDelegate("banner_ad_total_show_triggers", 0)

    // 累积展示统计（持久化）
    private var totalShowCount by DataStoreIntDelegate("banner_ad_total_shows", 0)

    // 当前广告的收益信息（临时存储）
    private var currentAdValue: AdValue? = null

    companion object {
        private const val TAG = "AdmobBannerAdController"
        private const val AD_TIMEOUT = 1 * 60 * 60 * 1000L
        private const val DEFAULT_CACHE_SIZE_PER_AD_UNIT = 1

        @Volatile
        private var INSTANCE: AdmobBannerAdController? = null

        fun getInstance(): AdmobBannerAdController {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AdmobBannerAdController().also { INSTANCE = it }
            }
        }
    }

    // 内存缓存池 - 存储预加载的广告
    private val adCachePool = mutableListOf<CachedBannerAd>()
    private val maxCacheSizePerAdUnit = DEFAULT_CACHE_SIZE_PER_AD_UNIT
    private val showContextByAd = IdentityHashMap<AdView, ShowContext>()

    /**
     * 缓存的Banner广告数据类
     */
    private data class CachedBannerAd(
        val adView: AdView,
        val adUnitId: String,
        val loadTime: Long = System.currentTimeMillis()
    ) {
        fun isExpired(): Boolean {
            return System.currentTimeMillis() - loadTime > AD_TIMEOUT
        }
    }

    private data class ShowContext(
        val sessionId: String,
        val isPreload: Boolean
    )

    private var bannerAdView: AdView? = null
    private var loadTime: Long = 0L

    // 当前正在显示的广告（用于资源释放）
    private var currentShowingAd: AdView? = null

    /**
     * 获取自适应Banner广告尺寸
     */
    private fun getAdSize(context: Context): AdSize {
        val widthPixels = ScreenUtils.getScreenWidth()
        val density = Resources.getSystem().displayMetrics.density
        val adWidth = (widthPixels / density).toInt()
        return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, adWidth)
    }

    private fun createBannerAdView(context: Context, adUnitId: String): AdView {
        return AdView(context).apply {
            this.adUnitId = adUnitId
            setAdSize(getAdSize(context))
        }
    }

    /**
     * 从缓存获取广告
     */
    private fun getCachedAd(adUnitId: String): CachedBannerAd? {
        synchronized(adCachePool) {
            val index = adCachePool.indexOfFirst { it.adUnitId == adUnitId && !it.isExpired() }
            return if (index != -1) adCachePool.removeAt(index) else null
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
     * 检查指定广告位的缓存是否已满
     */
    private fun isCacheFull(adUnitId: String): Boolean {
        return getCachedAdCount(adUnitId) >= maxCacheSizePerAdUnit
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
     * 加载Banner广告
     */
    private suspend fun loadAdInternal(context: Context, adUnitId: String): AdView? {
        totalLoadCount++
        AdLogger.d("AdmobBanner广告累积加载次数: $totalLoadCount")

        val requestId = AdEventReporter.reportStartLoad(AdType.BANNER, AdPlatform.ADMOB, adUnitId, totalLoadCount)

        return runAdmobLoadOnMainThread {
            suspendCancellableCoroutine { continuation ->
                val adView = createBannerAdView(context, adUnitId)
                var loadStartTime = System.currentTimeMillis()
                var currentAdUniqueId = ""

                adView.onPaidEventListener = OnPaidEventListener { adValue ->
                    AdLogger.d("AdmobBanner广告收益回调: value=${adValue.valueMicros}, currency=${adValue.currencyCode}")
                    val showContext = getShowContext(adView)

                    val uuid = java.util.UUID.randomUUID().toString()
                    val creativeId = adView.responseInfo?.loadedAdapterResponseInfo?.adSourceInstanceId.orEmpty()
                    currentAdUniqueId = "${uuid}_${adUnitId}_${creativeId}"
                    currentAdValue = adValue

                    AdEventReporter.reportImpression(
                        AdType.BANNER,
                        AdPlatform.ADMOB,
                        adUnitId,
                        currentAdUniqueId,
                        totalShowCount,
                        adView.responseInfo?.loadedAdapterResponseInfo?.adSourceName.orEmpty(),
                        currentAdValue?.let { it.valueMicros / 1_000_000.0 } ?: 0.0,
                        currentAdValue?.currencyCode ?: "",
                        sessionId = showContext.sessionId,
                        isPreload = showContext.isPreload
                    )

                    AdRevenueReporter.reportRevenue(
                        AdType.BANNER,
                        AdPlatform.ADMOB,
                        adUnitId,
                        adValue.valueMicros / 1_000_000.0,
                        adValue.currencyCode,
                        adView.responseInfo?.loadedAdapterResponseInfo?.adSourceName ?: "Admob",
                        adView.responseInfo?.loadedAdapterResponseInfo?.adSourceInstanceName.orEmpty()
                    )
                }

                adView.adListener = object : AdListener() {
                    override fun onAdLoaded() {
                        val elapsed = System.currentTimeMillis() - loadStartTime
                        AdLogger.d("AdmobBanner广告加载成功，广告位ID: %s, 耗时: %dms", adUnitId, elapsed)
                        totalLoadSucCount++
                        bannerAdView = adView
                        this@AdmobBannerAdController.loadTime = System.currentTimeMillis()
                        AdEventReporter.reportLoaded(
                            AdType.BANNER,
                            AdPlatform.ADMOB,
                            adUnitId,
                            totalLoadSucCount,
                            adView.responseInfo?.loadedAdapterResponseInfo?.adSourceName.orEmpty(),
                            ceil(elapsed / 1000.0).toInt(),
                            requestId
                        )
                        continuation.resume(adView)
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        val elapsed = System.currentTimeMillis() - loadStartTime
                        AdLogger.e("AdmobBanner ad load failed，广告位ID: %s, 耗时: %dms, 错误: %s", adUnitId, elapsed, error.message)
                        totalLoadFailCount++
                        AdEventReporter.reportLoadFail(
                            AdType.BANNER,
                            AdPlatform.ADMOB,
                            adUnitId,
                            totalLoadFailCount,
                            error.responseInfo?.loadedAdapterResponseInfo?.adSourceName.orEmpty(),
                            ceil(elapsed / 1000.0).toInt(),
                            error.message,
                            requestId
                        )
                        continuation.resume(null)
                    }

                    override fun onAdClicked() {
                        AdLogger.d("AdmobBanner广告被点击")
                        totalClickCount++
                        AdLogger.d("AdmobBanner广告累积点击次数: $totalClickCount")
                        AdConfigManager.recordClick(AdType.BANNER, AdPlatform.ADMOB)
                        AdClickProtectionController.recordClick(currentAdUniqueId)
                        AdEventReporter.reportClick(
                            AdType.BANNER,
                            AdPlatform.ADMOB,
                            adUnitId,
                            currentAdUniqueId,
                            totalClickCount,
                            adView.responseInfo?.loadedAdapterResponseInfo?.adSourceName.orEmpty(),
                            currentAdValue?.let { it.valueMicros / 1_000_000.0 } ?: 0.0,
                            currentAdValue?.currencyCode ?: ""
                        )
                    }

                    override fun onAdImpression() {
                        AdLogger.d("AdmobBanner广告展示完成")
                        totalShowCount++
                        AdLogger.d("AdmobBanner广告累积展示次数: $totalShowCount")
                        AdConfigManager.recordShow(AdType.BANNER, AdPlatform.ADMOB)
                    }

                    override fun onAdClosed() {
                        AdLogger.d("AdmobBanner广告关闭")
                        clearShowContext(adView)
                        totalCloseCount++
                        AdEventReporter.reportClose(
                            AdType.BANNER,
                            AdPlatform.ADMOB,
                            adUnitId,
                            totalCloseCount,
                            adView.responseInfo?.loadedAdapterResponseInfo?.adSourceName.orEmpty(),
                            (currentAdValue?.valueMicros ?: 0) / 1_000_000.0,
                            currentAdValue?.currencyCode ?: ""
                        )
                    }
                }

                adView.loadAd(AdRequest.Builder().build())
            }
        }
    }

    /**
     * 加载广告到缓存
     */
    private suspend fun loadAdToCache(context: Context, adUnitId: String): AdResult<Unit> {
        return try {
            val currentAdUnitCount = getCachedAdCount(adUnitId)
            if (currentAdUnitCount >= maxCacheSizePerAdUnit) {
                AdLogger.w("Admob广告位 %s 缓存已满，当前缓存: %d/%d", adUnitId, currentAdUnitCount, maxCacheSizePerAdUnit)
                return AdResult.Success(Unit)
            }

            val loadedAdView = loadAdInternal(context, adUnitId)
            if (loadedAdView != null) {
                synchronized(adCachePool) {
                    adCachePool.add(CachedBannerAd(loadedAdView, adUnitId))
                    val currentCount = getCachedAdCount(adUnitId)
                    AdLogger.d("AdmobBanner广告加载成功并缓存，广告位ID: %s，该广告位缓存数量: %d/%d", adUnitId, currentCount, maxCacheSizePerAdUnit)
                }
                AdResult.Success(Unit)
            } else {
                AdResult.Failure(createAdException("Admob banner ad load returned null"))
            }
        } catch (e: Exception) {
            AdLogger.e("AdmobBanner loadAdToCache异常", e)
            AdResult.Failure(AdException(0, "Admob banner ad loadAdToCache exception: ${e.message}", e))
        }
    }

    /**
     * 预加载Banner广告
     */
    suspend fun preloadAd(context: Context, adUnitId: String? = null): AdResult<Unit> {
        val finalAdUnitId = adUnitId ?: BillConfig.admob.bannerId
        return loadAdToCache(context, finalAdUnitId)
    }

    /**
     * 显示Banner广告（自动处理加载）
     */
    suspend fun showAd(context: FragmentActivity, container: ViewGroup, adUnitId: String? = null, sessionId: String = ""): AdResult<View> {
        val finalAdUnitId = adUnitId ?: BillConfig.admob.bannerId
        val showIsPreload = synchronized(adCachePool) { adCachePool.any { it.adUnitId == finalAdUnitId && !it.isExpired() } }
        var showingAd: AdView? = null

        totalShowTriggerCount++
        AdLogger.d("AdmobBanner广告累积触发展示次数: $totalShowTriggerCount")

        AdDestroyManager.instance.register(context) {
            AdLogger.d("AdmobBanner广告: Activity销毁，清理展示资源")
            destroyShowingAd()
            container.removeAllViews()
        }

        synchronized(adCachePool) {
            if (adCachePool.any { it.adUnitId == finalAdUnitId && it.isExpired() }) {
                AdEventReporter.reportTimeoutCache(AdType.BANNER, AdPlatform.ADMOB, finalAdUnitId)
            }
        }

        return try {
            var cachedAd = getCachedAd(finalAdUnitId)
            if (cachedAd == null) {
                AdLogger.d("Admob缓存为空，立即加载Banner广告，广告位ID: %s", finalAdUnitId)
                loadAdToCache(context, finalAdUnitId)
                cachedAd = getCachedAd(finalAdUnitId)
            }

            if (cachedAd != null) {
                AdLogger.d("Admob使用缓存中的Banner广告，广告位ID: %s", finalAdUnitId)
                container.removeAllViews()
                val adView = cachedAd.adView
                showingAd = adView
                (adView.parent as? ViewGroup)?.removeView(adView)
                rememberShowContext(adView, sessionId, showIsPreload)
                container.addView(adView)
                currentShowingAd = adView
                AdResult.Success(adView)
            } else {
                totalShowFailCount++
                AdLogger.d("AdmobBanner广告累积展示失败次数: $totalShowFailCount")
                AdEventReporter.reportShowFail(AdType.BANNER, AdPlatform.ADMOB, finalAdUnitId, totalShowFailCount, "No fill", sessionId = sessionId, isPreload = showIsPreload)
                AdResult.Failure(createAdException("Admob banner ad no cached ad available"))
            }
        } catch (e: Exception) {
            showingAd?.let { ad ->
                clearShowContext(ad)
                if (currentShowingAd == ad) {
                    currentShowingAd = null
                }
            }
            totalShowFailCount++
            AdEventReporter.reportShowFail(AdType.BANNER, AdPlatform.ADMOB, finalAdUnitId, totalShowFailCount, e.message.orEmpty(), sessionId = sessionId, isPreload = showIsPreload)
            AdLogger.e("Admob显示Banner广告失败", e)
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
     * 获取当前广告视图
     */
    fun getCurrentAdView(): AdView? {
        return if (!isAdExpired()) bannerAdView else null
    }

    /**
     * 检查是否有可用的广告
     */
    fun isAdLoaded(): Boolean {
        return bannerAdView != null && !isAdExpired()
    }

    /**
     * 检查广告是否已过期
     */
    fun isAdExpired(): Boolean {
        val expired = loadTime != 0L && System.currentTimeMillis() - loadTime > AD_TIMEOUT
        if (expired) {
            AdLogger.d("Admob Banner广告已过期")
        }
        return expired
    }

    /**
     * 获取剩余有效时间（毫秒）
     */
    fun getRemainingTime(): Long {
        if (loadTime == 0L) return 0L
        val remaining = AD_TIMEOUT - (System.currentTimeMillis() - loadTime)
        return if (remaining > 0) remaining else 0L
    }

    /**
     * 暂停广告
     */
    fun pauseAd() {
        currentShowingAd?.pause()
        AdLogger.d("Admob Banner广告已暂停")
    }

    /**
     * 恢复广告
     */
    fun resumeAd() {
        currentShowingAd?.resume()
        AdLogger.d("Admob Banner广告已恢复")
    }

    /**
     * 销毁广告
     */
    fun destroyAd() {
        synchronized(adCachePool) {
            adCachePool.forEach { cachedAd -> cachedAd.adView.destroy() }
            adCachePool.clear()
        }
        clearAllShowContexts()
        bannerAdView = null
        loadTime = 0L
        AdLogger.d("Admob Banner广告已销毁")
    }

    /**
     * 销毁正在显示的广告
     */
    fun destroyShowingAd() {
        currentShowingAd?.let { ad ->
            clearShowContext(ad)
            ad.destroy()
            AdLogger.d("Admob Banner广告正在显示的广告已销毁")
        }
        currentShowingAd = null
    }

    /**
     * 清理资源
     */
    fun destroy() {
        destroyAd()
        AdLogger.d("Admob Banner广告控制器已清理")
    }

    private fun rememberShowContext(adView: AdView, sessionId: String, isPreload: Boolean) {
        synchronized(showContextByAd) {
            showContextByAd[adView] = ShowContext(sessionId, isPreload)
        }
    }

    private fun getShowContext(adView: AdView): ShowContext {
        synchronized(showContextByAd) {
            return showContextByAd[adView] ?: ShowContext("", false)
        }
    }

    private fun clearShowContext(adView: AdView) {
        synchronized(showContextByAd) {
            showContextByAd.remove(adView)
        }
    }

    private fun clearAllShowContexts() {
        synchronized(showContextByAd) {
            showContextByAd.clear()
        }
    }
}
