package com.android.common.bill.ads.renderer

import android.content.Context
import android.view.View
import android.view.ViewGroup
import com.bytedance.sdk.openadsdk.api.nativeAd.PAGNativeAdData
import com.bytedance.sdk.openadsdk.api.nativeAd.PAGViewBinder

/**
 * Pangle 原生广告渲染器接口
 */
interface PangleNativeAdRenderer {
    /**
     * 创建原生广告布局
     */
    fun createLayout(context: Context): ViewGroup

    /**
     * 绑定广告数据到视图
     */
    fun bindData(context: Context, adView: ViewGroup, nativeAdData: PAGNativeAdData)

    /**
     * 创建 PAGViewBinder（用于 SDK 注册交互）
     */
    fun createViewBinder(container: ViewGroup, adView: ViewGroup): PAGViewBinder

    /**
     * 获取需要注册点击事件的 View 列表
     */
    fun getClickViews(adView: ViewGroup): List<View>
}
