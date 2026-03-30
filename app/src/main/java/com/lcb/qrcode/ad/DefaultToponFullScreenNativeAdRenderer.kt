package com.lcb.qrcode.ad

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.android.common.bill.ads.renderer.ToponFullScreenNativeAdRenderer
import com.bumptech.glide.Glide
import com.lcb.qrcode.R
import com.thinkup.nativead.api.TUNativeMaterial
import com.thinkup.nativead.api.TUNativePrepareInfo

/**
 * TopOn 全屏原生广告默认渲染器
 */
class DefaultToponFullScreenNativeAdRenderer : ToponFullScreenNativeAdRenderer {

    override fun createLayout(context: Context): ViewGroup {
        return LayoutInflater.from(context)
            .inflate(R.layout.layout_topon_fullscreen_native_ad, null) as ViewGroup
    }

    override fun bindData(adView: ViewGroup, material: TUNativeMaterial) {
        val titleView = adView.findViewById<TextView>(R.id.tv_ad_title)
        val ctaButton = adView.findViewById<TextView>(R.id.btn_ad_cta)
        val iconView = adView.findViewById<ImageView>(R.id.iv_ad_icon)
        val descView = adView.findViewById<TextView>(R.id.tv_ad_description)

        titleView?.text = material.title ?: "Test TopOn Ads"
        ctaButton?.text = material.callToActionText ?: "INSTALL"
        descView?.text = material.descriptionText ?: ""

        material.iconImageUrl?.let { iconUrl ->
            iconView?.let { view ->
                try {
                    Glide.with(view.context).load(iconUrl).into(view)
                    view.visibility = View.VISIBLE
                } catch (_: Exception) {
                    view.visibility = View.VISIBLE
                }
            }
        } ?: run {
            iconView?.setImageResource(android.R.drawable.ic_menu_info_details)
            iconView?.visibility = View.VISIBLE
        }

        // 处理主图
        material.mainImageUrl?.let { mainImageUrl ->
            val mediaContainer = adView.findViewById<ViewGroup>(R.id.fl_ad_media)
            mediaContainer?.let { container ->
                container.removeAllViews()
                val imageView = ImageView(container.context)
                imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                try {
                    Glide.with(container.context).load(mainImageUrl).into(imageView)
                } catch (_: Exception) {}
                container.addView(imageView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                container.visibility = View.VISIBLE
            }
        } ?: run {
            adView.findViewById<ViewGroup>(R.id.fl_ad_media)?.visibility = View.GONE
        }
    }

    override fun createPrepareInfo(adView: ViewGroup): TUNativePrepareInfo {
        val prepareInfo = TUNativePrepareInfo()
        adView.findViewById<TextView>(R.id.tv_ad_title)?.let {
            prepareInfo.setTitleView(it)
        }
        adView.findViewById<TextView>(R.id.tv_ad_description)?.let {
            prepareInfo.descView = it
        }
        adView.findViewById<TextView>(R.id.btn_ad_cta)?.let {
            prepareInfo.ctaView = it
        }
        adView.findViewById<ImageView>(R.id.iv_ad_icon)?.let {
            prepareInfo.setIconView(it)
        }
        adView.findViewById<ViewGroup>(R.id.fl_ad_media)?.let {
            prepareInfo.setMainImageView(it)
        }
        return prepareInfo
    }

    override fun createLoadingView(context: Context, container: ViewGroup) {
        container.removeAllViews()
        val loadingView = LayoutInflater.from(context)
            .inflate(R.layout.layout_fullscreen_loading, container, false)
        container.addView(loadingView)
    }
}
