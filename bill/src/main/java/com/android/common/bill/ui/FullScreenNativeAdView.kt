package com.android.common.bill.ui

import android.content.Context
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import com.google.android.gms.ads.nativead.NativeAd
import com.android.common.bill.BillConfig
import com.android.common.bill.ads.log.AdLogger

/**
 * 全屏原生广告UI视图组件
 * 封装全屏原生广告的布局创建、数据绑定和交互逻辑
 */
class FullScreenNativeAdView {
    
    companion object {
        private const val TAG = "FullScreenNativeAdView"
        private const val AUTO_CLOSE_DELAY = 10000L // 10秒自动关闭
    }
    
    /**
     * 创建并绑定全屏原生广告视图到容器中
     * @param context 上下文
     * @param container 目标容器
     * @param nativeAd 原生广告数据
     * @param lifecycleOwner 生命周期所有者（用于倒计时）
     * @param onCloseCallback 关闭回调
     * @return 是否绑定成功
     */
    fun bindFullScreenNativeAdToContainer(
        context: Context,
        container: ViewGroup,
        nativeAd: NativeAd,
        lifecycleOwner: LifecycleOwner
    ): Boolean {
        val renderer = BillConfig.admobFullScreenNativeRenderer
            ?: throw IllegalStateException("AdmobFullScreenNativeAdRenderer 未注册，请在 BillConfig 中设置 admobFullScreenNativeRenderer")

        return try {
            container.isVisible = true
            container.removeAllViews()
            
            val adView = renderer.createLayout(context)
            renderer.bindData(adView, nativeAd, lifecycleOwner)
            container.addView(adView)

            AdLogger.d("全屏原生广告视图绑定成功")
            true
        } catch (e: Exception) {
            AdLogger.e("全屏原生广告视图绑定失败", e)
            false
        }
    }
    
    /**
     * 创建全屏加载视图
     */
    fun createFullScreenLoadingView(
        context: Context,
        container: ViewGroup,
    ) {
        val renderer = BillConfig.admobFullScreenNativeRenderer
            ?: throw IllegalStateException("AdmobFullScreenNativeAdRenderer 未注册")
        try {
            renderer.createLoadingView(context, container)
        } catch (e: Exception) {
            AdLogger.e("创建全屏加载视图失败", e)
        }
    }
    
}
