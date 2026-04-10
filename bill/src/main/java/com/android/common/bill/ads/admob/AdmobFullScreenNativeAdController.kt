package com.android.common.bill.ads.admob

import android.content.Context
import android.view.ViewGroup
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LifecycleOwner
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdValue
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.OnPaidEventListener
import com.google.android.gms.ads.VideoOptions
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
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
import com.android.common.bill.ads.util.runAdmobLoadOnMainThread
import com.android.common.bill.ui.FullScreenNativeAdView
import net.corekit.core.ext.DataStoreIntDelegate
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.IdentityHashMap
import kotlin.coroutines.resume
import kotlin.math.ceil

/**
 * 全屏原生广告控制器
 * 专门处理全屏展示的原生广告
 */
class AdmobFullScreenNativeAdController private constructor() {
    @Volatile
    private var onAdDisplayedCallback: (() -> Unit)? = null

    private var totalClickCount by DataStoreIntDelegate("fullscreen_native_ad_total_clicks", 0)
    private var totalCloseCount by DataStoreIntDelegate("fullscreen_native_ad_total_close", 0)
    private var totalLoadCount by DataStoreIntDelegate("fullscreen_native_ad_total_loads", 0)
    private var totalLoadSucCount by DataStoreIntDelegate("fullscreen_native_ad_total_load_suc", 0)
    private var totalLoadFailCount by DataStoreIntDelegate("fullscreen_native_ad_total_load_fails", 0)
    private var totalShowFailCount by DataStoreIntDelegate("fullscreen_native_ad_total_show_fails", 0)
    private var totalShowTriggerCount by DataStoreIntDelegate("fullscreen_native_ad_total_show_triggers", 0)
    private var totalShowCount by DataStoreIntDelegate("fullscreen_native_ad_total_shows", 0)
    private var currentAdValue: AdValue? = null
    private var isShowing: Boolean = false

    companion object {
        private const val TAG = "AdmobFullScreenNativeAdController"
        private const val AD_TIMEOUT = 1 * 60 * 60 * 1000L
        private const val DEFAULT_CACHE_SIZE_PER_AD_UNIT = 1

        @Volatile
        private var INSTANCE: AdmobFullScreenNativeAdController? = null

        fun getInstance(): AdmobFullScreenNativeAdController {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AdmobFullScreenNativeAdController().also { INSTANCE = it }
            }
        }
    }

    private val adCachePool = mutableListOf<CachedFullScreenNativeAd>()
    private val maxCacheSizePerAdUnit = DEFAULT_CACHE_SIZE_PER_AD_UNIT

    private var fullScreenNativeAd: NativeAd? = null
    private var loadTime: Long = 0L
    private val fullScreenAdView = FullScreenNativeAdView()
    private val showContextByAd = IdentityHashMap<NativeAd, ShowContext>()
    private var currentShowingAd: NativeAd? = null

    private data class CachedFullScreenNativeAd(
        val ad: NativeAd,
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

    var nativeAds: NativeAd? = null

    suspend fun preloadAd(context: Context, adUnitId: String? = null): AdResult<Unit> {
        val finalAdUnitId = adUnitId ?: BillConfig.admob.fullNativeId

        val cached = synchronized(adCachePool) {
            adCachePool.firstOrNull { it.adUnitId == finalAdUnitId && !it.isExpired() }
        }
        if (cached != null) {
            AdLogger.d("Admob全屏原生广告已有有效缓存，广告位ID: %s", finalAdUnitId)
            return AdResult.Success(Unit)
        }

        return loadAdToCache(context, finalAdUnitId)
    }

    fun closeEvent(adUnitId: String = "") {
        totalCloseCount++
        AdEventReporter.reportClose(
            AdType.FULL_SCREEN_NATIVE,
            AdPlatform.ADMOB,
            adUnitId,
            totalCloseCount,
            nativeAds?.responseInfo?.loadedAdapterResponseInfo?.adSourceName.orEmpty(),
            (currentAdValue?.valueMicros ?: 0) / 1_000_000.0,
            currentAdValue?.currencyCode ?: ""
        )
        currentShowingAd?.let { clearShowContext(it) }
        isShowing = false
        onAdDisplayedCallback = null
    }

    suspend fun getAd(context: Context, adUnitId: String? = null): AdResult<NativeAd> {
        val finalAdUnitId = adUnitId ?: BillConfig.admob.fullNativeId
        var cachedAd = getCachedAd(finalAdUnitId)

        if (cachedAd == null) {
            AdLogger.d("Admob缓存为空，立即加载全屏原生广告，广告位ID: %s", finalAdUnitId)
            loadAdToCache(context, finalAdUnitId)
            cachedAd = getCachedAd(finalAdUnitId)
        }

        return if (cachedAd != null) {
            AdLogger.d("Admob使用缓存中的全屏原生广告，广告位ID: %s", finalAdUnitId)
            AdResult.Success(cachedAd.ad)
        } else {
            AdResult.Failure(createAdException("Admob full-screen native ad no cached ad available"))
        }
    }

    suspend fun showAdInContainer(
        context: Context,
        container: ViewGroup,
        lifecycleOwner: LifecycleOwner,
        adUnitId: String? = null,
        sessionId: String = "",
        onAdDisplayed: (() -> Unit)? = null
    ): AdResult<Unit> {
        val finalAdUnitId = adUnitId ?: BillConfig.admob.fullNativeId
        val showIsPreload = hasCachedAd(finalAdUnitId)
        var showingAd: NativeAd? = null

        totalShowTriggerCount++

        (context as? FragmentActivity)?.let { activity ->
            AdDestroyManager.instance.register(activity) {
                AdLogger.d("Admob全屏原生广告: Activity销毁，清理展示资源")
                destroyShowingAd()
                container.removeAllViews()
                isShowing = false
            }
        }

        onAdDisplayedCallback = onAdDisplayed

        synchronized(adCachePool) {
            if (adCachePool.any { it.adUnitId == finalAdUnitId && it.isExpired() }) {
                AdEventReporter.reportTimeoutCache(AdType.FULL_SCREEN_NATIVE, AdPlatform.ADMOB, finalAdUnitId)
            }
        }

        return try {
            fullScreenAdView.createFullScreenLoadingView(context, container)

            when (val result = getAd(context, finalAdUnitId)) {
                is AdResult.Success -> {
                    val nativeAd = result.data
                    showingAd = nativeAd
                    rememberShowContext(nativeAd, sessionId, showIsPreload)
                    val success = fullScreenAdView.bindFullScreenNativeAdToContainer(
                        context,
                        container,
                        nativeAd,
                        lifecycleOwner
                    )

                    if (success) {
                        currentShowingAd = nativeAd
                        AdResult.Success(Unit)
                    } else {
                        clearShowContext(nativeAd)
                        onAdDisplayedCallback = null
                        totalShowFailCount++
                        AdEventReporter.reportShowFail(
                            AdType.FULL_SCREEN_NATIVE,
                            AdPlatform.ADMOB,
                            finalAdUnitId,
                            totalShowFailCount,
                            "Admob full-screen native: ad bindView failed",
                            sessionId = sessionId,
                            isPreload = showIsPreload
                        )
                        AdResult.Failure(createAdException("Admob full-screen native: ad bindView failed"))
                    }
                }
                is AdResult.Failure -> {
                    onAdDisplayedCallback = null
                    totalShowFailCount++
                    AdLogger.e("Admob全屏原生ad load failed: %s", result.error.message)
                    AdEventReporter.reportShowFail(
                        AdType.FULL_SCREEN_NATIVE,
                        AdPlatform.ADMOB,
                        finalAdUnitId,
                        totalShowFailCount,
                        result.error.message,
                        sessionId = sessionId,
                        isPreload = showIsPreload
                    )
                    AdResult.Failure(result.error)
                }
            }
        } catch (e: Exception) {
            showingAd?.let { ad ->
                clearShowContext(ad)
                if (currentShowingAd == ad) {
                    currentShowingAd = null
                }
            }
            onAdDisplayedCallback = null
            totalShowFailCount++
            AdLogger.e("Admob显示全屏原生广告失败", e)
            AdEventReporter.reportShowFail(
                AdType.FULL_SCREEN_NATIVE,
                AdPlatform.ADMOB,
                finalAdUnitId,
                totalShowFailCount,
                e.message.orEmpty(),
                sessionId = sessionId,
                isPreload = showIsPreload
            )
            AdResult.Failure(AdException(code = -2, message = "show full-screen native ad exception: ${e.message}", cause = e))
        }
    }

    private fun getCachedAd(adUnitId: String): CachedFullScreenNativeAd? {
        synchronized(adCachePool) {
            val index = adCachePool.indexOfFirst { it.adUnitId == adUnitId && !it.isExpired() }
            return if (index != -1) adCachePool.removeAt(index) else null
        }
    }

    private fun getCachedAdCount(adUnitId: String): Int {
        synchronized(adCachePool) {
            return adCachePool.count { it.adUnitId == adUnitId && !it.isExpired() }
        }
    }

    private fun isCacheFull(adUnitId: String): Boolean {
        return getCachedAdCount(adUnitId) >= maxCacheSizePerAdUnit
    }

    fun hasCachedAd(adUnitId: String? = null): Boolean {
        synchronized(adCachePool) {
            return if (adUnitId != null) {
                adCachePool.any { it.adUnitId == adUnitId && !it.isExpired() }
            } else {
                adCachePool.any { !it.isExpired() }
            }
        }
    }

    private suspend fun loadAdToCache(context: Context, adUnitId: String): AdResult<Unit> {
        return try {
            val currentAdUnitCount = getCachedAdCount(adUnitId)
            if (currentAdUnitCount >= maxCacheSizePerAdUnit) {
                AdLogger.w("Admob广告位 %s 缓存已满，当前缓存: %d/%d", adUnitId, currentAdUnitCount, maxCacheSizePerAdUnit)
                return AdResult.Success(Unit)
            }

            val nativeAd = loadAd(context, adUnitId)
            if (nativeAd != null) {
                synchronized(adCachePool) {
                    adCachePool.add(CachedFullScreenNativeAd(nativeAd, adUnitId))
                    val currentCount = getCachedAdCount(adUnitId)
                    AdLogger.d("Admob全屏原生广告加载成功并缓存，广告位ID: %s，该广告位缓存数量: %d/%d", adUnitId, currentCount, maxCacheSizePerAdUnit)
                }
                AdResult.Success(Unit)
            } else {
                AdResult.Failure(createAdException("Admob full-screen native ad load returned null"))
            }
        } catch (e: Exception) {
            AdLogger.e("Admob全屏原生loadAdToCache异常", e)
            AdResult.Failure(AdException(0, "Admob full-screen native ad loadAdToCache exception: ${e.message}", e))
        }
    }

    private fun createAdException(message: String, cause: Throwable? = null): AdException {
        return AdException(
            code = -1,
            message = message,
            cause = cause
        )
    }

    private suspend fun loadAd(context: Context, adUnitId: String): NativeAd? {
        totalLoadCount++
        AdLogger.d("Admob全屏原生广告累积加载次数: $totalLoadCount")

        val requestId = AdEventReporter.reportStartLoad(AdType.FULL_SCREEN_NATIVE, AdPlatform.ADMOB, adUnitId, totalLoadCount)

        return runAdmobLoadOnMainThread {
            suspendCancellableCoroutine { continuation ->
                val startTime = System.currentTimeMillis()
                var currentAdUniqueId = ""
                var loadedNativeAd: NativeAd? = null
                val videoOptions = VideoOptions.Builder().setStartMuted(true).build()

                val adLoader = AdLoader.Builder(context, adUnitId)
                    .forNativeAd { nativeAd ->
                        loadedNativeAd = nativeAd
                        nativeAds = nativeAd
                        fullScreenNativeAd = nativeAd
                        loadTime = System.currentTimeMillis()
                        val elapsed = System.currentTimeMillis() - startTime
                        AdLogger.d("Admob全屏原生广告加载成功，广告位ID: %s, 耗时: %dms", adUnitId, elapsed)
                        totalLoadSucCount++
                        AdEventReporter.reportLoaded(
                            AdType.FULL_SCREEN_NATIVE,
                            AdPlatform.ADMOB,
                            adUnitId,
                            totalLoadSucCount,
                            nativeAd.responseInfo?.loadedAdapterResponseInfo?.adSourceName.orEmpty(),
                            ceil(elapsed / 1000.0).toInt(),
                            requestId
                        )

                        var hasImpressionCounted = false
                        nativeAd.setOnPaidEventListener(OnPaidEventListener { adValue ->
                            AdLogger.d("Admob全屏原生广告收益回调: value=${adValue.valueMicros}, currency=${adValue.currencyCode}")
                            val showContext = getShowContext(nativeAd)

                            val uuid = java.util.UUID.randomUUID().toString()
                            val creativeId = nativeAd.responseInfo?.loadedAdapterResponseInfo?.adSourceInstanceId.orEmpty()
                            currentAdUniqueId = "${uuid}_${adUnitId}_${creativeId}"
                            currentAdValue = adValue

                            if (!hasImpressionCounted) {
                                totalShowCount++
                                hasImpressionCounted = true
                                AdLogger.d("Admob全屏原生广告累积展示次数: $totalShowCount")
                                AdConfigManager.recordShow(AdType.FULL_SCREEN_NATIVE, AdPlatform.ADMOB)
                            }

                            AdEventReporter.reportImpression(
                                AdType.FULL_SCREEN_NATIVE,
                                AdPlatform.ADMOB,
                                adUnitId,
                                currentAdUniqueId,
                                totalShowCount,
                                nativeAd.responseInfo?.loadedAdapterResponseInfo?.adSourceName.orEmpty(),
                                currentAdValue?.let { it.valueMicros / 1_000_000.0 } ?: 0.0,
                                currentAdValue?.currencyCode ?: "",
                                sessionId = showContext.sessionId,
                                isPreload = showContext.isPreload
                            )

                            AdRevenueReporter.reportRevenue(
                                AdType.FULL_SCREEN_NATIVE,
                                AdPlatform.ADMOB,
                                adUnitId,
                                adValue.valueMicros / 1_000_000.0,
                                adValue.currencyCode,
                                nativeAd.responseInfo?.loadedAdapterResponseInfo?.adSourceName ?: "Admob",
                                nativeAd.responseInfo?.loadedAdapterResponseInfo?.adSourceInstanceName.orEmpty()
                            )
                        })

                        continuation.resume(nativeAd)
                    }
                    .withAdListener(object : AdListener() {
                        override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                            val elapsed = System.currentTimeMillis() - startTime
                            AdLogger.e("Admob全屏原生ad load failed，广告位ID: %s, 耗时: %dms, 错误: %s", adUnitId, elapsed, loadAdError.message)
                            totalLoadFailCount++
                            AdEventReporter.reportLoadFail(
                                AdType.FULL_SCREEN_NATIVE,
                                AdPlatform.ADMOB,
                                adUnitId,
                                totalLoadFailCount,
                                loadAdError.responseInfo?.loadedAdapterResponseInfo?.adSourceName.orEmpty(),
                                ceil(elapsed / 1000.0).toInt(),
                                loadAdError.message,
                                requestId
                            )
                            continuation.resume(null)
                        }

                        override fun onAdClicked() {
                            AdLogger.d("Admob全屏原生广告被点击")
                            totalClickCount++
                            AdLogger.d("Admob全屏原生广告累积点击次数: $totalClickCount")
                            AdConfigManager.recordClick(AdType.FULL_SCREEN_NATIVE, AdPlatform.ADMOB)
                            AdClickProtectionController.recordClick(currentAdUniqueId)
                            AdEventReporter.reportClick(
                                AdType.FULL_SCREEN_NATIVE,
                                AdPlatform.ADMOB,
                                adUnitId,
                                currentAdUniqueId,
                                totalClickCount,
                                nativeAds?.responseInfo?.loadedAdapterResponseInfo?.adSourceName.orEmpty(),
                                currentAdValue?.let { it.valueMicros / 1_000_000.0 } ?: 0.0,
                                currentAdValue?.currencyCode ?: ""
                            )
                        }

                        override fun onAdImpression() {
                            AdLogger.d("Admob全屏原生广告展示完成")
                            isShowing = true
                            if (!isCacheFull(adUnitId)) {
                                GlobalScope.launch {
                                    try {
                                        AdLogger.d("Admob全屏原生广告曝光，开始预缓存，广告位ID: %s", adUnitId)
                                        preloadAd(context, adUnitId)
                                    } catch (e: Exception) {
                                        AdLogger.e("Admob全屏原生广告预缓存失败", e)
                                    }
                                }
                            }
                            AdLogger.d("Admob全屏原生广告显示成功")
                            notifyAdDisplayedIfNeeded()
                        }

                        override fun onAdClosed() {
                            super.onAdClosed()
                            onAdDisplayedCallback = null
                            loadedNativeAd?.let { clearShowContext(it) }
                            totalCloseCount++
                            AdEventReporter.reportClose(
                                AdType.FULL_SCREEN_NATIVE,
                                AdPlatform.ADMOB,
                                adUnitId,
                                totalCloseCount,
                                nativeAds?.responseInfo?.loadedAdapterResponseInfo?.adSourceName.orEmpty(),
                                (currentAdValue?.valueMicros ?: 0) / 1_000_000.0,
                                currentAdValue?.currencyCode ?: ""
                            )
                        }
                    })
                    .withNativeAdOptions(
                        NativeAdOptions.Builder()
                            .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
                            .setMediaAspectRatio(NativeAdOptions.NATIVE_MEDIA_ASPECT_RATIO_LANDSCAPE)
                            .setVideoOptions(videoOptions)
                            .build()
                    )
                    .build()

                adLoader.loadAd(AdRequest.Builder().build())
            }
        }
    }

    private fun notifyAdDisplayedIfNeeded() {
        val callback = onAdDisplayedCallback ?: return
        onAdDisplayedCallback = null
        try {
            callback.invoke()
        } catch (e: Exception) {
            AdLogger.e("Admob全屏原生 onAdDisplayed 回调异常", e)
        }
    }

    fun getCurrentAd(): NativeAd? {
        return if (!isAdExpired()) fullScreenNativeAd else null
    }

    fun isAdLoaded(): Boolean {
        return fullScreenNativeAd != null && !isAdExpired()
    }

    fun isAdExpired(): Boolean {
        val expired = loadTime != 0L && System.currentTimeMillis() - loadTime > AD_TIMEOUT
        if (expired) {
            AdLogger.d("Admob全屏原生广告已过期")
        }
        return expired
    }

    fun getRemainingTime(): Long {
        if (loadTime == 0L) return 0L
        val remaining = AD_TIMEOUT - (System.currentTimeMillis() - loadTime)
        return if (remaining > 0) remaining else 0L
    }

    fun destroyAd() {
        synchronized(adCachePool) {
            adCachePool.forEach { cachedAd -> cachedAd.ad.destroy() }
            adCachePool.clear()
        }
        clearAllShowContexts()
        fullScreenNativeAd = null
        loadTime = 0L
        AdLogger.d("Admob全屏原生广告已销毁")
    }

    fun destroyShowingAd() {
        currentShowingAd?.let { ad ->
            clearShowContext(ad)
            ad.destroy()
            AdLogger.d("Admob全屏原生广告正在显示的广告已销毁")
        }
        currentShowingAd = null
    }

    fun destroy() {
        destroyAd()
        AdLogger.d("全屏原生广告控制器已清理")
    }

    fun isAdShowing(): Boolean {
        return isShowing
    }

    private fun rememberShowContext(ad: NativeAd, sessionId: String, isPreload: Boolean) {
        synchronized(showContextByAd) {
            showContextByAd[ad] = ShowContext(sessionId, isPreload)
        }
    }

    private fun getShowContext(ad: NativeAd): ShowContext {
        synchronized(showContextByAd) {
            return showContextByAd[ad] ?: ShowContext("", false)
        }
    }

    private fun clearShowContext(ad: NativeAd) {
        synchronized(showContextByAd) {
            showContextByAd.remove(ad)
        }
    }

    private fun clearAllShowContexts() {
        synchronized(showContextByAd) {
            showContextByAd.clear()
        }
    }
}
