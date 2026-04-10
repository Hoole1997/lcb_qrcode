package com.android.common.bill.ads.renderer

import android.content.Context
import android.view.ViewGroup
import com.thinkup.nativead.api.TUNativeMaterial
import com.thinkup.nativead.api.TUNativePrepareInfo

/**
 * TopOn 原生广告渲染器接口
 * 仅控制自渲染模式，模板渲染由 SDK 自动处理
 */
interface ToponNativeAdRenderer {
    /**
     * 创建原生广告布局（自渲染模式）
     */
    fun createLayout(context: Context): ViewGroup

    /**
     * 绑定广告数据到视图
     */
    fun bindData(adView: ViewGroup, material: TUNativeMaterial)

    /**
     * 创建 TUNativePrepareInfo（绑定交互视图）
     */
    fun createPrepareInfo(adView: ViewGroup): TUNativePrepareInfo
}
