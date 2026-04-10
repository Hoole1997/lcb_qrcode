package com.android.common.bill

import com.android.common.bill.ads.renderer.AdLoadingDialogRenderer
import com.android.common.bill.ui.NativeAdStyle
import com.android.common.bill.ui.pangle.PangleNativeAdStyle
import com.android.common.bill.ui.topon.ToponNativeAdStyle
import com.android.common.bill.ads.renderer.AdmobFullScreenNativeAdRenderer
import com.android.common.bill.ads.renderer.AdmobNativeAdRenderer
import com.android.common.bill.ads.renderer.PangleFullScreenNativeAdRenderer
import com.android.common.bill.ads.renderer.PangleNativeAdRenderer
import com.android.common.bill.ads.renderer.ToponFullScreenNativeAdRenderer
import com.android.common.bill.ads.renderer.ToponNativeAdRenderer

/**
 * Bill 模块统一配置
 * 宿主项目在初始化时注入广告平台 ID、SDK Key 和渲染器
 */
object BillConfig {

    // ==================== 广告平台配置 ====================
    var admob: AdmobConfig = AdmobConfig()
    var pangle: PangleConfig = PangleConfig()
    var topon: ToponConfig = ToponConfig()

    // ==================== 渲染器（null 时使用内置默认实现） ====================
    var admobNativeRenderer: AdmobNativeAdRenderer? = null
    var admobFullScreenNativeRenderer: AdmobFullScreenNativeAdRenderer? = null
    var pangleNativeRenderer: PangleNativeAdRenderer? = null
    var pangleFullScreenNativeRenderer: PangleFullScreenNativeAdRenderer? = null
    var toponNativeRenderer: ToponNativeAdRenderer? = null
    var toponFullScreenNativeRenderer: ToponFullScreenNativeAdRenderer? = null

    // ==================== Loading 弹框渲染器 ====================
    var adLoadingDialogRenderer: AdLoadingDialogRenderer? = null

    // ==================== AdMob 配置 ====================
    data class AdmobConfig(
        var applicationId: String = "",
        var splashId: String = "",
        var bannerId: String = "",
        var interstitialId: String = "",
        var nativeId: String = "",
        var fullNativeId: String = "",
        var rewardedId: String = "",
        var nativeStyleStandard: NativeAdStyle = NativeAdStyle(0, "normal"),
        var nativeStyleLarge: NativeAdStyle = NativeAdStyle(0, "card")
    )

    // ==================== Pangle 配置 ====================
    data class PangleConfig(
        var applicationId: String = "",
        var splashId: String = "",
        var bannerId: String = "",
        var interstitialId: String = "",
        var nativeId: String = "",
        var fullNativeId: String = "",
        var rewardedId: String = "",
        var nativeStyleStandard: PangleNativeAdStyle = PangleNativeAdStyle(0),
        var nativeStyleLarge: PangleNativeAdStyle = PangleNativeAdStyle(0)
    )

    // ==================== TopOn 配置 ====================
    data class ToponConfig(
        var applicationId: String = "",
        var appKey: String = "",
        var interstitialId: String = "",
        var rewardedId: String = "",
        var nativeId: String = "",
        var splashId: String = "",
        var fullNativeId: String = "",
        var bannerId: String = "",
        var nativeStyleStandard: ToponNativeAdStyle = ToponNativeAdStyle(0, "normal"),
        var nativeStyleLarge: ToponNativeAdStyle = ToponNativeAdStyle(0, "large", 146)
    )

}
