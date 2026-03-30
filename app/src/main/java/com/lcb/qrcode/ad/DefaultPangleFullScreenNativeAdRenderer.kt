package com.lcb.qrcode.ad

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.android.common.bill.ads.renderer.PangleFullScreenNativeAdRenderer
import com.bumptech.glide.Glide
import com.bytedance.sdk.openadsdk.api.nativeAd.PAGNativeAdData
import com.bytedance.sdk.openadsdk.api.nativeAd.PAGViewBinder
import com.lcb.qrcode.R

/**
 * Pangle 全屏原生广告默认渲染器
 */
class DefaultPangleFullScreenNativeAdRenderer : PangleFullScreenNativeAdRenderer {

    override fun createLayout(context: Context): ViewGroup {
        return LayoutInflater.from(context)
            .inflate(R.layout.layout_pangle_fullscreen_native_ad, null) as ViewGroup
    }

    override fun bindData(context: Context, adView: ViewGroup, nativeAdData: PAGNativeAdData) {
        val titleView = adView.findViewById<TextView>(R.id.tv_ad_title)
        val descView = adView.findViewById<TextView>(R.id.tv_ad_description)
        val ctaView = adView.findViewById<TextView>(R.id.btn_ad_cta)
        val iconView = adView.findViewById<ImageView>(R.id.iv_ad_icon)
        val mediaContainer = adView.findViewById<FrameLayout>(R.id.fl_ad_media)
        val logoContainer = adView.findViewById<FrameLayout>(R.id.fl_ad_logo)

        titleView?.text = nativeAdData.title ?: ""
        descView?.text = nativeAdData.description ?: ""
        ctaView?.text = nativeAdData.buttonText ?: "INSTALL"

        nativeAdData.icon?.let { icon ->
            try {
                iconView?.let { Glide.with(context).load(icon.imageUrl).into(it) }
                iconView?.visibility = View.VISIBLE
            } catch (_: Exception) {
                iconView?.visibility = View.GONE
            }
        } ?: run {
            iconView?.visibility = View.GONE
        }

        mediaContainer?.let { container ->
            container.removeAllViews()
            nativeAdData.mediaView?.let { mediaView ->
                container.addView(mediaView)
                container.visibility = View.VISIBLE
            } ?: run {
                container.visibility = View.GONE
            }
        }

        logoContainer?.let { container ->
            container.removeAllViews()
            nativeAdData.adLogoView?.let { logoView ->
                container.addView(logoView)
                container.visibility = View.VISIBLE
            } ?: run {
                container.visibility = View.GONE
            }
        }
    }

    override fun createViewBinder(container: ViewGroup, adView: ViewGroup): PAGViewBinder {
        return PAGViewBinder.Builder(container)
            .titleTextView(adView.findViewById<TextView>(R.id.tv_ad_title))
            .descriptionTextView(adView.findViewById<TextView>(R.id.tv_ad_description))
            .logoViewGroup(adView.findViewById<FrameLayout>(R.id.fl_ad_logo))
            .iconImageView(adView.findViewById<ImageView>(R.id.iv_ad_icon))
            .mediaContentViewGroup(adView.findViewById<FrameLayout>(R.id.fl_ad_media))
            .build()
    }

    override fun getClickViews(adView: ViewGroup): List<View> {
        return listOfNotNull(
            adView.findViewById<TextView>(R.id.tv_ad_title),
            adView.findViewById<TextView>(R.id.tv_ad_description),
            adView.findViewById<TextView>(R.id.btn_ad_cta),
            adView.findViewById<ImageView>(R.id.iv_ad_icon)
        )
    }

    override fun createLoadingView(context: Context, container: ViewGroup) {
        container.removeAllViews()
        val loadingView = LayoutInflater.from(context)
            .inflate(R.layout.layout_fullscreen_loading, container, false)
        container.addView(loadingView)
    }
}
