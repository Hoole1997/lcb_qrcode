package com.android.common.bill.ui.topon

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.android.common.bill.R
import com.android.common.bill.ads.log.AdLogger
import com.thinkup.banner.api.TUBannerView

/**
 * TopOn Banner广告UI视图组件
 * 封装TopOn Banner广告的布局创建和显示逻辑
 */
class ToponBannerAdView {
    
    companion object {
        private const val TAG = "ToponBannerAdView"
    }
    
    /**
     * 创建并绑定TopOn Banner广告视图到容器中
     * @param context 上下文
     * @param container 目标容器
     * @param bannerView TopOn的TUBannerView
     * @param onExpandCallback 展开状态变化回调（已弃用，传null即可）
     * @return 是否绑定成功
     */
    fun bindBannerAdToContainer(
        context: Context,
        container: ViewGroup,
        bannerView: TUBannerView,
        onExpandCallback: ((Boolean) -> Unit)? = null
    ): Boolean {
        return try {
            // 清空容器
            container.removeAllViews()
            
            // 创建Banner广告容器布局
            val bannerContainer = createBannerContainerLayout(context)
            
            // 将TUBannerView添加到容器中
            val adContainer = bannerContainer.findViewById<FrameLayout>(R.id.fl_ad_container)
            adContainer.removeAllViews()
            adContainer.addView(bannerView)
            
            // 添加到目标容器
            container.addView(bannerContainer)

            AdLogger.d("TopOn Banner广告视图绑定成功")
            true
        } catch (e: Exception) {
            AdLogger.e("TopOn Banner广告视图绑定失败", e)
            false
        }
    }

    /**
     * 创建Banner容器布局
     */
    private fun createBannerContainerLayout(context: Context): View {
        return LayoutInflater.from(context).inflate(R.layout.layout_banner_container, null).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }
    
    /**
     * 重置状态
     */
    fun reset() {
        // TopOn Banner广告重置，目前无需特殊处理
    }
}

