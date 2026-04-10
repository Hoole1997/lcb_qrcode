package com.android.common.bill.ads.renderer

import android.content.Context
import android.view.ViewGroup
import androidx.lifecycle.LifecycleOwner
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView

/**
 * Admob 全屏原生广告渲染器接口
 */
interface AdmobFullScreenNativeAdRenderer {
    /**
     * 创建全屏原生广告布局
     * @return 必须返回 AdMob SDK 的 NativeAdView
     */
    fun createLayout(context: Context): NativeAdView

    /**
     * 绑定广告数据到视图
     * 实现方需要设置 headlineView、bodyView、callToActionView、iconView、mediaView 等，
     * 并调用 adView.registerNativeAd(nativeAd, mediaView)
     */
    fun bindData(adView: NativeAdView, nativeAd: NativeAd, lifecycleOwner: LifecycleOwner)

    /**
     * 创建全屏加载视图
     */
    fun createLoadingView(context: Context, container: ViewGroup)
}
