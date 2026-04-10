package com.android.common.bill.ui.pangle

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import com.bytedance.sdk.openadsdk.api.nativeAd.PAGNativeAd
import com.bytedance.sdk.openadsdk.api.nativeAd.PAGNativeAdInteractionCallback
import com.android.common.bill.BillConfig
import com.android.common.bill.ads.log.AdLogger

/**
 * Pangle全屏原生广告视图组件
 * 提供全屏原生广告的布局创建、数据绑定和交互注册
 */
class PangleFullScreenNativeAdView {

    companion object {
        private const val TAG = "PangleFullScreenNativeView"
    }

    /**
     * 创建并绑定全屏原生广告视图到容器中
     */
    fun bindFullScreenNativeAdToContainer(
        context: Context,
        container: ViewGroup,
        nativeAd: PAGNativeAd,
        @Suppress("UNUSED_PARAMETER") lifecycleOwner: LifecycleOwner? = null,
        interactionListener: PAGNativeAdInteractionCallback? = null
    ): Boolean {
        val renderer = BillConfig.pangleFullScreenNativeRenderer
            ?: throw IllegalStateException("PangleFullScreenNativeAdRenderer 未注册")

        return try {
            container.isVisible = true
            container.removeAllViews()

            val adView = renderer.createLayout(context)
            val nativeAdData = nativeAd.nativeAdData
            renderer.bindData(context, adView, nativeAdData)
            container.addView(adView)

            val clickViews = ArrayList(renderer.getClickViews(adView))
            val binder = renderer.createViewBinder(container, adView)

            @Suppress("UNCHECKED_CAST")
            nativeAd.registerViewForInteraction(
                binder,
                clickViews as MutableList<View>,
                interactionListener
            )

            true
        } catch (e: Exception) {
            AdLogger.e("Pangle全屏原生广告视图绑定失败", e)
            false
        }
    }

    /**
     * 创建加载视图
     */
    fun createFullScreenLoadingView(context: Context, container: ViewGroup) {
        val renderer = BillConfig.pangleFullScreenNativeRenderer
            ?: throw IllegalStateException("PangleFullScreenNativeAdRenderer 未注册")
        try {
            renderer.createLoadingView(context, container)
        } catch (e: Exception) {
            AdLogger.e("Pangle全屏原生加载视图创建失败", e)
        }
    }
}
