package com.android.common.bill.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import com.google.android.gms.ads.nativead.NativeAd
import com.android.common.bill.BillConfig
import com.android.common.bill.ads.log.AdLogger
import com.android.common.bill.ads.renderer.AdmobNativeAdRenderer

/**
 * 原生广告UI视图组件
 * 封装原生广告的布局创建和数据绑定逻辑
 */
class NativeAdView {

    companion object {
        private const val TAG = "NativeAdView"
    }

    /**
     * 创建并绑定原生广告视图到容器中
     * @param context 上下文
     * @param container 目标容器
     * @param nativeAd 原生广告数据
     * @param style 广告样式，默认为标准样式
     * @return 是否绑定成功
     */
    fun bindNativeAdToContainer(
        context: Context,
        container: ViewGroup,
        nativeAd: NativeAd,
        style: NativeAdStyle = BillConfig.admob.nativeStyleStandard
    ): Boolean {
        val renderer = BillConfig.admobNativeRenderer
            ?: throw IllegalStateException("AdmobNativeAdRenderer 未注册，请在 BillConfig 中设置 admobNativeRenderer")

        return try {
            container.isVisible = true
            container.removeAllViews()

            // 通过渲染器创建布局
            val adView = renderer.createLayout(context)

            // 通过渲染器绑定数据
            renderer.bindData(adView, nativeAd)

            // 添加到容器
            container.addView(adView)

            AdLogger.d("原生广告视图绑定成功")
            true
        } catch (e: Exception) {
            AdLogger.e("原生广告视图绑定失败", e)
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
            gravity = android.view.Gravity.CENTER
            setPadding(16, 16, 16, 16)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }

}
