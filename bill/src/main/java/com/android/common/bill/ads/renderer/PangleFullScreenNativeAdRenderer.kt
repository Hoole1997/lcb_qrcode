package com.android.common.bill.ads.renderer

import android.content.Context
import android.view.View
import android.view.ViewGroup
import com.bytedance.sdk.openadsdk.api.nativeAd.PAGNativeAdData
import com.bytedance.sdk.openadsdk.api.nativeAd.PAGViewBinder

/**
 * Pangle 全屏原生广告渲染器接口
 */
interface PangleFullScreenNativeAdRenderer {
    /**
     * 创建全屏原生广告布局
     */
    fun createLayout(context: Context): ViewGroup

    /**
     * 绑定广告数据到视图
     */
    fun bindData(context: Context, adView: ViewGroup, nativeAdData: PAGNativeAdData)

    /**
     * 创建 PAGViewBinder
     */
    fun createViewBinder(container: ViewGroup, adView: ViewGroup): PAGViewBinder

    /**
     * 获取点击视图列表
     */
    fun getClickViews(adView: ViewGroup): List<View>

    /**
     * 创建加载视图
     */
    fun createLoadingView(context: Context, container: ViewGroup)
}
