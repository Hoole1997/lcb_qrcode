package com.android.common.bill.ads

import android.content.Context
import com.android.common.bill.BillConfig
import com.android.common.bill.ads.admob.AdmobFullScreenNativeAdController
import com.android.common.bill.ads.admob.AdmobInterstitialAdController
import com.android.common.bill.ads.admob.AdmobNativeAdController
import com.android.common.bill.ads.log.AdLogger
import com.android.common.bill.ads.pangle.PangleFullScreenNativeAdController
import com.android.common.bill.ads.pangle.PangleInterstitialAdController
import com.android.common.bill.ads.pangle.PangleNativeAdController
import com.android.common.bill.ads.topon.TopOnFullScreenNativeAdController
import com.android.common.bill.ads.topon.TopOnInterstitialAdController
import com.android.common.bill.ads.topon.TopOnNativeAdController
import com.android.common.bill.ads.bidding.BiddingPlatformController
import com.android.common.bill.ads.config.AdPlatform
import com.android.common.bill.ads.config.AdType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object PreloadController {

    // 共享协程作用域，避免每次调用都创建新的 Scope
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ==================== 聚合预加载函数 ====================

    /**
     * 预加载所有平台的广告（并行执行）
     */
    fun preloadAll(context: Context) {
        scope.launch { preloadAdmobInternal(context) }
        scope.launch { preloadPangleInternal(context) }
        scope.launch { preloadTopOnInternal(context) }
    }

    /**
     * 预加载所有聚合平台的全屏原生广告（并行执行）
     */
    fun preloadAllFullScreenNative(context: Context) {
        scope.launch { preloadAdmobFullScreenNativeInternal(context) }
        scope.launch { preloadTopOnFullScreenNativeInternal(context) }
        scope.launch { preloadPangleFullScreenNativeInternal(context) }
    }

    // ==================== AdMob ====================

    fun preloadAdmob(context: Context) {
        scope.launch { preloadAdmobInternal(context) }
    }

    fun preloadAdmobInterstitial(context: Context) {
        scope.launch { preloadAdmobInterstitialInternal(context) }
    }

    fun preloadAdmobNative(context: Context) {
        scope.launch { preloadAdmobNativeInternal(context) }
    }

    fun preloadAdmobFullScreenNative(context: Context) {
        scope.launch { preloadAdmobFullScreenNativeInternal(context) }
    }

    private suspend fun preloadAdmobInternal(context: Context) {
        preloadAdmobNativeInternal(context)
        preloadAdmobInterstitialInternal(context)
    }

    private suspend fun preloadAdmobInterstitialInternal(context: Context) {
        try {
            AdLogger.d("Admob插页开始异步预加载，广告位ID: %s", BillConfig.admob.interstitialId)
            AdmobInterstitialAdController.getInstance().preloadAd(context, BillConfig.admob.interstitialId)
        } catch (e: Exception) {
            AdLogger.e("Admob插页异步预加载广告失败", e)
        }
    }

    private suspend fun preloadAdmobNativeInternal(context: Context) {
        try {
            AdLogger.d("Admob原生开始异步预加载，广告位ID: %s", BillConfig.admob.nativeId)
            AdmobNativeAdController.getInstance().preloadAd(context, BillConfig.admob.nativeId)
        } catch (e: Exception) {
            AdLogger.e("Admob原生异步预加载广告失败", e)
        }
    }

    private suspend fun preloadAdmobFullScreenNativeInternal(context: Context) {
        try {
            AdLogger.d("Admob全屏原生开始异步预加载，广告位ID: %s", BillConfig.admob.fullNativeId)
            AdmobFullScreenNativeAdController.getInstance().preloadAd(context, BillConfig.admob.fullNativeId)
        } catch (e: Exception) {
            AdLogger.e("Admob全屏原生异步预加载广告失败", e)
        }
    }

    // ==================== Pangle ====================

    fun preloadPangle(context: Context) {
        scope.launch { preloadPangleInternal(context) }
    }

    fun preloadPangleInterstitial(context: Context) {
        scope.launch { preloadPangleInterstitialInternal(context) }
    }

    fun preloadPangleNative(context: Context) {
        scope.launch { preloadPangleNativeInternal(context) }
    }

    fun preloadPangleFullScreenNative(context: Context) {
        scope.launch { preloadPangleFullScreenNativeInternal(context) }
    }

    private suspend fun preloadPangleInternal(context: Context) {
        preloadPangleNativeInternal(context)
        preloadPangleInterstitialInternal(context)
    }

    private suspend fun preloadPangleInterstitialInternal(context: Context) {
        if (!BiddingPlatformController.isPlatformEnabled(AdType.INTERSTITIAL, AdPlatform.PANGLE)) return
        try {
            AdLogger.d("Pangle插页开始异步预加载，广告位ID: %s", BillConfig.pangle.interstitialId)
            PangleInterstitialAdController.getInstance().preloadAd(context, BillConfig.pangle.interstitialId)
        } catch (e: Exception) {
            AdLogger.e("Pangle插页异步预加载广告失败", e)
        }
    }

    private suspend fun preloadPangleNativeInternal(context: Context) {
        if (!BiddingPlatformController.isPlatformEnabled(AdType.NATIVE, AdPlatform.PANGLE)) return
        try {
            AdLogger.d("Pangle原生开始异步预加载，广告位ID: %s", BillConfig.pangle.nativeId)
            PangleNativeAdController.getInstance().preloadAd(context, BillConfig.pangle.nativeId)
        } catch (e: Exception) {
            AdLogger.e("Pangle原生异步预加载广告失败", e)
        }
    }

    private suspend fun preloadPangleFullScreenNativeInternal(context: Context) {
        if (!BiddingPlatformController.isPlatformEnabled(AdType.FULL_SCREEN_NATIVE, AdPlatform.PANGLE)) return
        try {
            AdLogger.d("Pangle全屏原生开始异步预加载，广告位ID: %s", BillConfig.pangle.fullNativeId)
            PangleFullScreenNativeAdController.getInstance().preloadAd(context, BillConfig.pangle.fullNativeId)
        } catch (e: Exception) {
            AdLogger.e("Pangle全屏原生异步预加载广告失败", e)
        }
    }

    // ==================== TopOn ====================

    fun preloadTopOn(context: Context) {
        scope.launch { preloadTopOnInternal(context) }
    }

    fun preloadTopOnInterstitial(context: Context) {
        scope.launch { preloadTopOnInterstitialInternal(context) }
    }

    fun preloadTopOnNative(context: Context) {
        scope.launch { preloadTopOnNativeInternal(context) }
    }

    fun preloadTopOnFullScreenNative(context: Context) {
        scope.launch { preloadTopOnFullScreenNativeInternal(context) }
    }

    private suspend fun preloadTopOnInternal(context: Context) {
        preloadTopOnNativeInternal(context)
        preloadTopOnInterstitialInternal(context)
    }

    private suspend fun preloadTopOnInterstitialInternal(context: Context) {
        if (!BiddingPlatformController.isPlatformEnabled(AdType.INTERSTITIAL, AdPlatform.TOPON)) return
        try {
            AdLogger.d("TopOn插页开始异步预加载，广告位ID: %s", BillConfig.topon.interstitialId)
            TopOnInterstitialAdController.getInstance().preloadAd(context, BillConfig.topon.interstitialId)
        } catch (e: Exception) {
            AdLogger.e("TopOn插页异步预加载广告失败", e)
        }
    }

    private suspend fun preloadTopOnNativeInternal(context: Context) {
        if (!BiddingPlatformController.isPlatformEnabled(AdType.NATIVE, AdPlatform.TOPON)) return
        try {
            AdLogger.d("TopOn原生开始异步预加载，广告位ID: %s", BillConfig.topon.nativeId)
            TopOnNativeAdController.getInstance().preloadAd(context, BillConfig.topon.nativeId)
        } catch (e: Exception) {
            AdLogger.e("TopOn原生异步预加载广告失败", e)
        }
    }

    private suspend fun preloadTopOnFullScreenNativeInternal(context: Context) {
        if (!BiddingPlatformController.isPlatformEnabled(AdType.FULL_SCREEN_NATIVE, AdPlatform.TOPON)) return
        try {
            AdLogger.d("TopOn全屏原生开始异步预加载，广告位ID: %s", BillConfig.topon.fullNativeId)
            TopOnFullScreenNativeAdController.getInstance().preloadAd(context, BillConfig.topon.fullNativeId)
        } catch (e: Exception) {
            AdLogger.e("TopOn全屏原生异步预加载广告失败", e)
        }
    }

}
