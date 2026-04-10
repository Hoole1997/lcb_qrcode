package com.lcb.qrcode.ad

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.android.common.bill.ads.renderer.PangleNativeAdRenderer
import com.android.common.bill.ui.pangle.PangleNativeAdStyle
import com.bumptech.glide.Glide
import com.bytedance.sdk.openadsdk.api.nativeAd.PAGNativeAdData
import com.bytedance.sdk.openadsdk.api.nativeAd.PAGViewBinder
import com.lcb.qrcode.R

/**
 * Pangle 原生广告默认渲染器
 */
class DefaultPangleNativeAdRenderer(
    private val layoutResId: Int = R.layout.layout_pangle_native_ads
) : PangleNativeAdRenderer {

    override fun createLayout(context: Context): ViewGroup {
        return LayoutInflater.from(context)
            .inflate(layoutResId, null) as ViewGroup
    }

    override fun bindData(context: Context, adView: ViewGroup, nativeAdData: PAGNativeAdData) {
        val titleView = adView.findViewById<TextView>(R.id.tv_ad_title)
        val ctaButton = adView.findViewById<TextView>(R.id.btn_ad_cta)
        val iconView = adView.findViewById<ImageView>(R.id.iv_ad_icon)
        val descView = adView.findViewById<TextView>(R.id.tv_ad_description)
        val logoContainer = adView.findViewById<FrameLayout>(R.id.fl_ad_logo)

        titleView?.text = nativeAdData.title ?: ""
        ctaButton?.text = nativeAdData.buttonText ?: "INSTALL"
        descView?.text = nativeAdData.description ?: ""

        nativeAdData.icon?.let { icon ->
            iconView?.let { view ->
                try {
                    Glide.with(context).load(icon.imageUrl).into(view)
                    view.visibility = View.VISIBLE
                } catch (_: Exception) {
                    view.visibility = View.GONE
                }
            }
        } ?: run {
            iconView?.visibility = View.GONE
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
}