package com.android.common.bill.ads.admob

import android.content.Context
import android.view.ViewGroup
import androidx.fragment.app.FragmentActivity
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
import com.android.common.bill.ui.NativeAdStyle
import com.android.common.bill.ui.NativeAdView
import net.corekit.core.ext.DataStoreIntDelegate
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.IdentityHashMap
import kotlin.coroutines.resume
import kotlin.math.ceil

/**
 * 原生广告控制器
 * 提供原生广告的加载和管理功能
 */
class AdmobNativeAdController private constructor() {

    // 累积点击统计（持久化）
    private var totalClickCount by DataStoreIntDelegate("native_ad_total_clicks", 0)

    // 累积关闭统计（持久化）
    private var totalCloseCount by DataStoreIntDelegate("native_ad_total_close", 0)

    // 累积加载次数统计（持久化）
    private var totalLoadCount by DataStoreIntDelegate("native_ad_total_loads", 0)

    // 累积加载成功次数统计（持久化）
    private var totalLoadSucCount by DataStoreIntDelegate("native_ad_total_load_suc", 0)

    // 累积加载失败次数统计（持久化）
    private var totalLoadFailCount by DataStoreIntDelegate("native_ad_total_load_fails", 0)

    // 累积展示失败次数统计（持久化）
    private var totalShowFailCount by DataStoreIntDelegate("native_ad_total_show_fails", 0)

    // 累积触发统计（持久化）
    private var totalShowTriggerCount by DataStoreIntDelegate("native_ad_total_show_triggers", 0)

    // 累积展示统计（持久化）
    private var totalShowCount by DataStoreIntDelegate("native_ad_total_shows", 0)

    // 当前广告的收益信息（临时存储）
    private var currentAdValue: AdValue? = null

    companion object {
        private const val TAG = "AdmobNativeAdController"
        private const val AD_TIMEOUT = 1 * 60 * 60 * 1000L
        private const val DEFAULT_CACHE_SIZE_PER_AD_UNIT = 1

        @Volatile
        private var INSTANCE: AdmobNativeAdController? = null

        fun getInstance(): AdmobNativeAdController {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AdmobNativeAdController().also { INSTANCE = it }
            }
        }
    }

    private val adCachePool = mutableListOf<CachedNativeAd>()
    private val maxCacheSizePerAdUnit = DEFAULT_CACHE_SIZE_PER_AD_UNIT
    private val nativeAdView = NativeAdView()
    private val showContextByAd = IdentityHashMap<NativeAd, ShowContext>()

    // 当前正在显示的广告（用于资源释放）
    private var currentShowingAd: NativeAd? = null

    private data class ShowContext(
        val sessionId: String,
        val isPreload: Boolean
    )

    private data class CachedNativeAd(
        val ad: NativeAd,
        val adUnitId: String,
        val loadTime: Long = System.currentTimeMillis()
    ) {
        fun isExpired(): Boolean {
            return System.currentTimeMillis() - loadTime > AD_TIMEOUT
        }
    }

    suspend fun preloadAd(context: Context, adUnitId: String? = null): AdResult<Unit> {
        val finalAdUnitId = adUnitId ?: BillConfig.admob.nativeId

        val cached = synchronized(adCachePool) {
            adCachePool.firstOrNull { it.adUnitId == finalAdUnitId && !it.isExpired() }
        }
        if (cached != null) {
            AdLogger.d("Admob原生广告已有有效缓存，广告位ID: %s", finalAdUnitId)
            return AdResult.Success(Unit)
        }

        return loadAdToCache(context, finalAdUnitId)
    }

    suspend fun getAd(context: Context, adUnitId: String? = null): AdResult<NativeAd> {
        val finalAdUnitId = adUnitId ?: BillConfig.admob.nativeId
        var cachedAd = getCachedAd(finalAdUnitId)

        if (cachedAd == null) {
            AdLogger.d("Admob缓存为空，立即加载原生广告，广告位ID: %s", finalAdUnitId)
            loadAdToCache(context, finalAdUnitId)
            cachedAd = getCachedAd(finalAdUnitId)
        }

        return if (cachedAd != null) {
            AdLogger.d("Admob使用缓存中的原生广告，广告位ID: %s", finalAdUnitId)
            AdResult.Success(cachedAd.ad)
        } else {
            AdResult.Failure(createAdException("Admob native ad no cached ad available"))
        }
    }

    suspend fun showAdInContainer(
        context: Context,
        container: ViewGroup,
        style: NativeAdStyle = BillConfig.admob.nativeStyleStandard,
        adUnitId: String? = null,
        sessionId: String = ""
    ): Boolean {
        val finalAdUnitId = adUnitId ?: BillConfig.admob.nativeId
        val showIsPreload = synchronized(adCachePool) { adCachePool.any { it.adUnitId == finalAdUnitId && !it.isExpired() } }
        var showingAd: NativeAd? = null

        totalShowTriggerCount++
        AdLogger.d("Admob原生广告累积触发展示次数: $totalShowTriggerCount")

        (context as? FragmentActivity)?.let { activity ->
            AdDestroyManager.instance.register(activity) {
                AdLogger.d("Admob原生广告: Activity销毁，清理展示资源")
                destroyShowingAd()
                container.removeAllViews()
            }
        }

        synchronized(adCachePool) {
            if (adCachePool.any { it.adUnitId == finalAdUnitId && it.isExpired() }) {
                AdEventReporter.reportTimeoutCache(AdType.NATIVE, AdPlatform.ADMOB, finalAdUnitId)
            }
        }

        return try {
            when (val result = getAd(context, finalAdUnitId)) {
                is AdResult.Success -> {
                    val nativeAd = result.data
                    showingAd = nativeAd
                    currentShowingAd = nativeAd
                    rememberShowContext(nativeAd, sessionId, showIsPreload)
                    val bindSuccess = nativeAdView.bindNativeAdToContainer(context, container, nativeAd, style)
                    if (bindSuccess) {
                        true
                    } else {
                        totalShowFailCount++
                        clearShowContext(nativeAd)
                        if (currentShowingAd == nativeAd) {
                            currentShowingAd = null
                        }
                        AdEventReporter.reportShowFail(
                            AdType.NATIVE,
                            AdPlatform.ADMOB,
                            finalAdUnitId,
                            totalShowFailCount,
                            "bind_failed",
                            sessionId = sessionId,
                            isPreload = showIsPreload
                        )
                        false
                    }
                }
                is AdResult.Failure -> {
                    totalShowFailCount++
                    AdEventReporter.reportShowFail(
                        AdType.NATIVE,
                        AdPlatform.ADMOB,
                        finalAdUnitId,
                        totalShowFailCount,
                        result.error.message,
                        sessionId = sessionId,
                        isPreload = showIsPreload
                    )
                    false
                }
            }
        } catch (e: Exception) {
            showingAd?.let { ad ->
                clearShowContext(ad)
                if (currentShowingAd == ad) {
                    currentShowingAd = null
                }
            }
            totalShowFailCount++
            AdEventReporter.reportShowFail(
                AdType.NATIVE,
                AdPlatform.ADMOB,
                finalAdUnitId,
                totalShowFailCount,
                e.message.orEmpty(),
                sessionId = sessionId,
                isPreload = showIsPreload
            )
            AdLogger.e("Admob显示原生广告失败", e)
            false
        }
    }

    private suspend fun loadAd(context: Context, adUnitId: String): NativeAd? {
        totalLoadCount++
        AdLogger.d("Admob原生广告累积加载次数: $totalLoadCount")

        val requestId = AdEventReporter.reportStartLoad(AdType.NATIVE, AdPlatform.ADMOB, adUnitId, totalLoadCount)

        return runAdmobLoadOnMainThread {
            suspendCancellableCoroutine { continuation ->
                val startTime = System.currentTimeMillis()
                var nativeAds: NativeAd? = null
                var currentAdUniqueId = ""
                val videoOptions = VideoOptions.Builder().setStartMuted(true).build()

                val adLoader = AdLoader.Builder(context, adUnitId)
                    .forNativeAd { nativeAd ->
                        nativeAds = nativeAd
                        val elapsed = System.currentTimeMillis() - startTime
                        AdLogger.d("Admob原生广告加载成功，广告位ID: %s, 耗时: %dms", adUnitId, elapsed)
                        totalLoadSucCount++
                        AdEventReporter.reportLoaded(
                            AdType.NATIVE,
                            AdPlatform.ADMOB,
                            adUnitId,
                            totalLoadSucCount,
                            nativeAd.responseInfo?.loadedAdapterResponseInfo?.adSourceName.orEmpty(),
                            ceil(elapsed / 1000.0).toInt(),
                            requestId
                        )

                        nativeAd.setOnPaidEventListener(OnPaidEventListener { adValue ->
                            AdLogger.d("Admob原生广告收益回调: value=${adValue.valueMicros}, currency=${adValue.currencyCode}")
                            val showContext = getShowContext(nativeAd)

                            val uuid = java.util.UUID.randomUUID().toString()
                            val creativeId = nativeAd.responseInfo?.loadedAdapterResponseInfo?.adSourceInstanceId.orEmpty()
                            currentAdUniqueId = "${uuid}_${adUnitId}_${creativeId}"
                            currentAdValue = adValue

                            AdEventReporter.reportImpression(
                                AdType.NATIVE,
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
                                AdType.NATIVE,
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
                            AdLogger.e("Admob原生ad load failed，广告位ID: %s, 耗时: %dms, 错误: %s", adUnitId, elapsed, loadAdError.message)

                            totalLoadFailCount++
                            AdEventReporter.reportLoadFail(
                                AdType.NATIVE,
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
                            AdLogger.d("Admob原生广告被点击")
                            totalClickCount++
                            AdLogger.d("Admob原生广告累积点击次数: $totalClickCount")
                            AdConfigManager.recordClick(AdType.NATIVE, AdPlatform.ADMOB)
                            AdClickProtectionController.recordClick(currentAdUniqueId)
                            AdEventReporter.reportClick(
                                AdType.NATIVE,
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
                            AdLogger.d("Admob原生广告展示完成")
                            totalShowCount++
                            AdLogger.d("Admob原生广告累积展示次数: $totalShowCount")
                            AdConfigManager.recordShow(AdType.NATIVE, AdPlatform.ADMOB)
                            if (!isCacheFull(adUnitId)) {
                                GlobalScope.launch {
                                    try {
                                        AdLogger.d("Admob原生广告曝光，开始预缓存，广告位ID: %s", adUnitId)
                                        preloadAd(context, adUnitId)
                                    } catch (e: Exception) {
                                        AdLogger.e("Admob原生广告预缓存失败", e)
                                    }
                                }
                            }
                        }

                        override fun onAdClosed() {
                            super.onAdClosed()
                            nativeAds?.let { clearShowContext(it) }
                            totalCloseCount++
                            AdEventReporter.reportClose(
                                AdType.NATIVE,
                                AdPlatform.ADMOB,
                                adUnitId,
                                totalCloseCount,
                                nativeAds?.responseInfo?.loadedAdapterResponseInfo?.adSourceName.orEmpty(),
                                currentAdValue?.let { it.valueMicros / 1_000_000.0 } ?: 0.0,
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
                    adCachePool.add(CachedNativeAd(nativeAd, adUnitId))
                    val currentCount = getCachedAdCount(adUnitId)
                    AdLogger.d("Admob原生广告加载成功并缓存，广告位ID: %s，该广告位缓存数量: %d/%d", adUnitId, currentCount, maxCacheSizePerAdUnit)
                }
                AdResult.Success(Unit)
            } else {
                AdResult.Failure(createAdException("Admob native ad load returned null"))
            }
        } catch (e: Exception) {
            AdLogger.e("Admob原生loadAdToCache异常", e)
            AdResult.Failure(AdException(0, "Admob native ad loadAdToCache exception: ${e.message}", e))
        }
    }

    private fun getCachedAd(adUnitId: String): CachedNativeAd? {
        synchronized(adCachePool) {
            val index = adCachePool.indexOfFirst { it.adUnitId == adUnitId && !it.isExpired() }
            return if (index != -1) adCachePool.removeAt(index) else null
        }
    }

    fun peekCurrentAd(adUnitId: String? = null): NativeAd? {
        return synchronized(adCachePool) {
            adCachePool.firstOrNull { it.adUnitId == adUnitId && !it.isExpired() }?.ad
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

    fun getCurrentAd(): NativeAd? {
        return getCachedAd(BillConfig.admob.nativeId)?.ad
    }

    fun isAdLoaded(): Boolean {
        return getCachedAdCount(BillConfig.admob.nativeId) > 0
    }

    fun destroyAd() {
        synchronized(adCachePool) {
            adCachePool.forEach { cachedAd -> cachedAd.ad.destroy() }
            adCachePool.clear()
        }
        clearAllShowContexts()
        AdLogger.d("Admob原生广告已销毁")
    }

    fun destroyShowingAd() {
        currentShowingAd?.let { ad ->
            clearShowContext(ad)
            ad.destroy()
            AdLogger.d("Admob原生广告正在显示的广告已销毁")
        }
        currentShowingAd = null
    }

    fun destroy() {
        destroyAd()
        AdLogger.d("Admob原生广告控制器已清理")
    }

    private fun createAdException(message: String, cause: Throwable? = null): AdException {
        return AdException(
            code = 0,
            message = message,
            cause = cause
        )
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
