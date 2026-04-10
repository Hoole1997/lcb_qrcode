package com.android.common.bill.ui.topon

import android.content.Context
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import com.android.common.bill.BillConfig
import com.android.common.bill.ads.log.AdLogger
import com.thinkup.nativead.api.NativeAd
import com.thinkup.nativead.api.TUNativeAdView

/**
 * TopOn全屏原生广告视图组件
 * 提供全屏原生广告的布局创建、数据绑定和交互注册
 */
class ToponFullScreenNativeAdView {

    companion object {
        private const val TAG = "ToponFullScreenNativeAdView"
    }

    /**
     * 创建并绑定全屏原生广告视图到容器中
     */
    fun bindFullScreenNativeAdToContainer(
        context: Context,
        container: ViewGroup,
        nativeAd: NativeAd,
        lifecycleOwner: LifecycleOwner
    ): Boolean {
        return try {
            container.isVisible = true
            container.removeAllViews()

            val isNativeExpress = nativeAd.isNativeExpress()
            if (isNativeExpress) {
                bindTemplateAd(context, container, nativeAd)
            } else {
                bindSelfRenderAd(context, container, nativeAd)
            }

            AdLogger.d("TopOn全屏原生广告视图绑定成功")
            true
        } catch (e: Exception) {
            AdLogger.e("TopOn全屏原生广告视图绑定失败", e)
            false
        }
    }

    /**
     * 创建加载视图
     */
    fun createFullScreenLoadingView(context: Context, container: ViewGroup) {
        val renderer = BillConfig.toponFullScreenNativeRenderer
            ?: throw IllegalStateException("ToponFullScreenNativeAdRenderer 未注册")
        try {
            renderer.createLoadingView(context, container)
        } catch (e: Exception) {
            AdLogger.e("TopOn全屏原生加载视图创建失败", e)
        }
    }

    /**
     * 绑定模板渲染广告
     */
    private fun bindTemplateAd(
        context: Context,
        container: ViewGroup,
        nativeAd: NativeAd
    ) {
        try {
            val nativeAdView = TUNativeAdView(context)
            nativeAd.renderAdContainer(nativeAdView, null)
            nativeAd.prepare(nativeAdView, null)
            container.addView(nativeAdView)
            AdLogger.d("TopOn全屏模板渲染广告绑定完成")
        } catch (e: Exception) {
            AdLogger.e("TopOn全屏模板渲染广告绑定失败", e)
            throw e
        }
    }

    /**
     * 绑定自渲染广告
     */
    private fun bindSelfRenderAd(
        context: Context,
        container: ViewGroup,
        nativeAd: NativeAd
    ) {
        val renderer = BillConfig.toponFullScreenNativeRenderer
            ?: throw IllegalStateException("ToponFullScreenNativeAdRenderer 未注册")

        try {
            val adView = renderer.createLayout(context)
            val nativeAdView = TUNativeAdView(context)
            val material = nativeAd.getAdMaterial()
            renderer.bindData(adView, material)
            val prepareInfo = renderer.createPrepareInfo(adView)
            nativeAd.renderAdContainer(nativeAdView, adView)
            nativeAd.prepare(nativeAdView, prepareInfo)
            container.addView(nativeAdView)
            AdLogger.d("TopOn全屏自渲染广告绑定完成")
        } catch (e: Exception) {
            AdLogger.e("TopOn全屏自渲染广告绑定失败", e)
            throw e
        }
    }
}

