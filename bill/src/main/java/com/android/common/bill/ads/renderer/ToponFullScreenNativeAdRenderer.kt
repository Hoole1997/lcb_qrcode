package com.android.common.bill.ads.renderer

import android.content.Context
import android.view.ViewGroup
import com.thinkup.nativead.api.TUNativeMaterial
import com.thinkup.nativead.api.TUNativePrepareInfo

/**
 * TopOn 全屏原生广告渲染器接口
 */
interface ToponFullScreenNativeAdRenderer {
    /**
     * 创建全屏原生广告布局（自渲染模式）
     */
    fun createLayout(context: Context): ViewGroup

    /**
     * 绑定广告数据到视图
     */
    fun bindData(adView: ViewGroup, material: TUNativeMaterial)

    /**
     * 创建 TUNativePrepareInfo
     */
    fun createPrepareInfo(adView: ViewGroup): TUNativePrepareInfo

    /**
     * 创建加载视图
     */
    fun createLoadingView(context: Context, container: ViewGroup)
}
