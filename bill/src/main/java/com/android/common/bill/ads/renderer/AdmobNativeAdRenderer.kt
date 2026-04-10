package com.android.common.bill.ads.renderer

import android.content.Context
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView

/**
 * Admob 原生广告渲染器接口
 * 宿主项目实现此接口以自定义原生广告的布局和数据绑定
 */
interface AdmobNativeAdRenderer {
    /**
     * 创建原生广告布局
     * @param context 上下文
     * @return 必须返回 AdMob SDK 的 NativeAdView
     */
    fun createLayout(context: Context): NativeAdView

    /**
     * 绑定广告数据到视图
     * 实现方需要设置 headlineView、bodyView、callToActionView、iconView 等，
     * 并调用 adView.registerNativeAd(nativeAd, null)
     * @param adView 由 createLayout 返回的 NativeAdView
     * @param nativeAd 原生广告数据
     */
    fun bindData(adView: NativeAdView, nativeAd: NativeAd)
}
