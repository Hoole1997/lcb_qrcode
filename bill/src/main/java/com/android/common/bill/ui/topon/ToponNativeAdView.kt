package com.android.common.bill.ui.topon

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import com.android.common.bill.BillConfig
import com.android.common.bill.ads.log.AdLogger
import com.thinkup.nativead.api.NativeAd
import com.thinkup.nativead.api.TUNativeAdView

/**
 * TopOn原生广告UI视图组件
 * 封装TopOn原生广告的布局创建和数据绑定逻辑
 */
class ToponNativeAdView {

    companion object {
        private const val TAG = "ToponNativeAdView"
    }

    /**
     * 创建并绑定TopOn原生广告视图到容器中
     * @param context 上下文
     * @param container 目标容器
     * @param nativeAd TopOn原生广告对象
     * @param style 广告样式，默认为标准样式
     * @return 是否绑定成功
     */
    fun bindNativeAdToContainer(
        context: Context,
        container: ViewGroup,
        nativeAd: NativeAd,
        style: ToponNativeAdStyle = BillConfig.topon.nativeStyleStandard
    ): Boolean {
        return try {
            container.isVisible = true
            // 清空容器
            container.removeAllViews()

            // 判断是自渲染还是模板渲染
            val isNativeExpress = nativeAd.isNativeExpress()
            
            if (isNativeExpress) {
                // 模板渲染：直接使用广告平台返回的渲染好的view
                bindTemplateAd(context, container, nativeAd)
            } else {
                // 自渲染：通过素材拼接
                bindSelfRenderAd(context, container, nativeAd, style)
            }

            AdLogger.d("TopOn原生广告视图绑定成功")
            true
        } catch (e: Exception) {
            AdLogger.e("TopOn原生广告视图绑定失败", e)
            false
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
            // 创建 TUNativeAdView
            val nativeAdView = TUNativeAdView(context)
            
            // 渲染模板广告（View参数传null）
            nativeAd.renderAdContainer(nativeAdView, null)
            
            // 准备广告（TUNativePrepareInfo参数传null）
            nativeAd.prepare(nativeAdView, null)
            
            // 添加到容器
            container.addView(nativeAdView)
            
            AdLogger.d("TopOn模板渲染广告绑定完成")
        } catch (e: Exception) {
            AdLogger.e("TopOn模板渲染广告绑定失败", e)
            throw e
        }
    }

    /**
     * 绑定自渲染广告
     */
    private fun bindSelfRenderAd(
        context: Context,
        container: ViewGroup,
        nativeAd: NativeAd,
        style: ToponNativeAdStyle
    ) {
        val renderer = BillConfig.toponNativeRenderer
            ?: throw IllegalStateException("ToponNativeAdRenderer 未注册")

        try {
            val adView = renderer.createLayout(context)
            val nativeAdView = TUNativeAdView(context)
            val material = nativeAd.getAdMaterial()
            renderer.bindData(adView, material)
            val prepareInfo = renderer.createPrepareInfo(adView)
            nativeAd.renderAdContainer(nativeAdView, adView)
            nativeAd.prepare(nativeAdView, prepareInfo)
            container.addView(nativeAdView)
            
            AdLogger.d("TopOn自渲染广告绑定完成")
        } catch (e: Exception) {
            AdLogger.e("TopOn自渲染广告绑定失败", e)
            throw e
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
            gravity = android.view.Gravity.CENTER
            setPadding(16, 16, 16, 16)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }
}

