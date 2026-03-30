package com.lcb.qrcode

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import com.android.common.bill.BillConfig
import com.android.common.bill.BillConfig.adLoadingDialogRenderer
import com.android.common.bill.BillConfig.admob
import com.android.common.bill.BillConfig.admobFullScreenNativeRenderer
import com.android.common.bill.BillConfig.admobNativeRenderer
import com.android.common.bill.BillConfig.pangle
import com.android.common.bill.BillConfig.pangleFullScreenNativeRenderer
import com.android.common.bill.BillConfig.pangleNativeRenderer
import com.android.common.bill.BillConfig.topon
import com.android.common.bill.BillConfig.toponFullScreenNativeRenderer
import com.android.common.bill.BillConfig.toponNativeRenderer
import com.android.common.bill.ads.bidding.AppOpenBiddingInitializer
import com.android.common.bill.ads.log.AdLogger
import com.android.common.bill.ui.NativeAdStyle
import com.android.common.bill.ui.pangle.PangleNativeAdStyle
import com.android.common.bill.ui.topon.ToponNativeAdStyle
import com.lcb.qrcode.ad.DefaultAdLoadingDialogRenderer
import com.lcb.qrcode.ad.DefaultAdmobFullScreenNativeAdRenderer
import com.lcb.qrcode.ad.DefaultAdmobNativeAdRenderer
import com.lcb.qrcode.ad.DefaultPangleFullScreenNativeAdRenderer
import com.lcb.qrcode.ad.DefaultPangleNativeAdRenderer
import com.lcb.qrcode.ad.DefaultToponFullScreenNativeAdRenderer
import com.lcb.qrcode.ad.DefaultToponNativeAdRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.corekit.core.controller.ChannelUserController
import net.corekit.core.log.CoreLogger
import net.corekit.metrics.log.MetricsLogger

class LcbQrCodeApp : Application() {

    companion object {
        var logEnable = false
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        logEnable = BuildConfig.FLAVOR.lowercase() == "local"
        MetricsLogger.enableLog(logEnable)
        CoreLogger.setLogEnabled(logEnable)
        AdLogger.setLogEnabled(logEnable)
        ChannelUserController.setDefaultChannel(BuildConfig.DEFAULT_USER_CHANNEL)
    }

    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        initAd()
    }

    private fun initAd() {
        appScope.launch {
            runCatching {
                AppOpenBiddingInitializer.initialize(this@LcbQrCodeApp, R.mipmap.ic_home_qr) {
                    admob = BillConfig.AdmobConfig(
                        applicationId = BuildConfig.ADMOB_APPLICATION_ID,
                        splashId = BuildConfig.ADMOB_SPLASH_ID,
                        bannerId = BuildConfig.ADMOB_BANNER_ID,
                        interstitialId = BuildConfig.ADMOB_INTERSTITIAL_ID,
                        nativeId = BuildConfig.ADMOB_NATIVE_ID,
                        fullNativeId = BuildConfig.ADMOB_FULL_NATIVE_ID,
                        rewardedId = BuildConfig.ADMOB_REWARDED_ID,
                        nativeStyleStandard = NativeAdStyle(R.layout.layout_native_ads, "normal"),
                        nativeStyleLarge = NativeAdStyle(R.layout.layout_native_ad_card, "card"),
                    )
                    pangle = BillConfig.PangleConfig(
                        applicationId = BuildConfig.PANGLE_APPLICATION_ID,
                        splashId = BuildConfig.PANGLE_SPLASH_ID,
                        bannerId = BuildConfig.PANGLE_BANNER_ID,
                        interstitialId = BuildConfig.PANGLE_INTERSTITIAL_ID,
                        nativeId = BuildConfig.PANGLE_NATIVE_ID,
                        fullNativeId = BuildConfig.PANGLE_FULL_NATIVE_ID,
                        rewardedId = BuildConfig.PANGLE_REWARDED_ID,
                        nativeStyleStandard = PangleNativeAdStyle(R.layout.layout_pangle_native_ads),
                        nativeStyleLarge = PangleNativeAdStyle(R.layout.layout_pangle_native_ads_large),
                    )
                    topon = BillConfig.ToponConfig(
                        applicationId = BuildConfig.TOPON_APPLICATION_ID,
                        appKey = BuildConfig.TOPON_APP_KEY,
                        interstitialId = BuildConfig.TOPON_INTERSTITIAL_ID,
                        rewardedId = BuildConfig.TOPON_REWARDED_ID,
                        nativeId = BuildConfig.TOPON_NATIVE_ID,
                        splashId = BuildConfig.TOPON_SPLASH_ID,
                        fullNativeId = BuildConfig.TOPON_FULL_NATIVE_ID,
                        bannerId = BuildConfig.TOPON_BANNER_ID,
                        nativeStyleStandard = ToponNativeAdStyle(
                            R.layout.layout_topon_native_ads,
                            "normal",
                            72
                        ),
                        nativeStyleLarge = ToponNativeAdStyle(
                            R.layout.layout_topon_native_ads_large,
                            "large",
                            146
                        ),
                    )
                    admobNativeRenderer = DefaultAdmobNativeAdRenderer()
                    admobFullScreenNativeRenderer = DefaultAdmobFullScreenNativeAdRenderer()
                    pangleNativeRenderer = DefaultPangleNativeAdRenderer()
                    pangleFullScreenNativeRenderer = DefaultPangleFullScreenNativeAdRenderer()
                    toponNativeRenderer = DefaultToponNativeAdRenderer()
                    toponFullScreenNativeRenderer = DefaultToponFullScreenNativeAdRenderer()
                    adLoadingDialogRenderer = DefaultAdLoadingDialogRenderer()
                }
            }.onFailure { throwable ->
                Log.e("LcbQrCodeApp", "Failed to initialize ads", throwable)
            }
        }
    }
}
