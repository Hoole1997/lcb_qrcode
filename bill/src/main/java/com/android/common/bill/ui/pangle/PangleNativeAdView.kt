package com.android.common.bill.ui.pangle

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import com.bytedance.sdk.openadsdk.api.nativeAd.PAGNativeAd
import com.bytedance.sdk.openadsdk.api.nativeAd.PAGNativeAdInteractionCallback
import com.android.common.bill.BillConfig
import com.android.common.bill.ads.log.AdLogger
import java.util.ArrayList

/**
 * Pangle原生广告UI视图组件
 * 封装Pangle原生广告的布局创建和数据绑定逻辑
 */
class PangleNativeAdView {

    companion object {
        private const val TAG = "PangleNativeAdView"
    }

    /**
     * 创建并绑定Pangle原生广告视图到容器中
     * @param context 上下文
     * @param container 目标容器（根视图）
     * @param nativeAd Pangle原生广告对象
     * @param style 广告样式，默认为标准样式
     * @param clickViews 需要注册普通点击事件的View集合（可选）
     * @param creativeViews 需要注册创意点击事件的View集合（可选）
     * @param dislikeView 自定义关闭View（可选）
     * @return 是否绑定成功
     */
    fun bindNativeAdToContainer(
        context: Context,
        container: ViewGroup,
        nativeAd: PAGNativeAd,
        @Suppress("UNUSED_PARAMETER") style: PangleNativeAdStyle = BillConfig.pangle.nativeStyleStandard,
        clickViews: List<View>? = null,
        dislikeView: View? = null,
        interactionListener: PAGNativeAdInteractionCallback? = null
    ): Boolean {
        val renderer = BillConfig.pangleNativeRenderer
            ?: throw IllegalStateException("PangleNativeAdRenderer 未注册，请在 BillConfig 中设置 pangleNativeRenderer")

        return try {
            container.isVisible = true
            container.removeAllViews()

            val adView = renderer.createLayout(context)
            val nativeAdData = nativeAd.getNativeAdData()
            renderer.bindData(context, adView, nativeAdData)
            container.addView(adView)

            val clickViewsList = ArrayList<View>().apply {
                clickViews?.forEach { add(it) }
                if (clickViews.isNullOrEmpty()) {
                    addAll(renderer.getClickViews(adView))
                }
            }

            val binder = renderer.createViewBinder(container, adView)
            @Suppress("UNCHECKED_CAST")
            nativeAd.registerViewForInteraction(
                binder,
                clickViewsList as MutableList<View>,
                interactionListener
            )

            AdLogger.d("Pangle原生广告视图绑定成功")
            true
        } catch (e: Exception) {
            AdLogger.e("Pangle原生广告视图绑定失败", e)
            false
        }
    }

    /**
     * 创建加载失败的占位视图
     */
    fun createErrorView(context: Context, errorMessage: String? = null): View {
        return TextView(context).apply {
            text = errorMessage ?: "广告加载失败"
            textSize = 12f
            setTextColor(0xFF999999.toInt())
            gravity = Gravity.CENTER
            setPadding(16, 16, 16, 16)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }
}
