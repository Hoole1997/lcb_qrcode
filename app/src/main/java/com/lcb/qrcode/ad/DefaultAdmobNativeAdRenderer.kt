package com.lcb.qrcode.ad

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.android.common.bill.ads.renderer.AdmobNativeAdRenderer
import com.android.common.bill.ui.NativeAdStyle
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.lcb.qrcode.R

/**
 * Admob 原生广告默认渲染器
 */
class DefaultAdmobNativeAdRenderer(
    private val layoutResId: Int = R.layout.layout_native_ads
) : AdmobNativeAdRenderer {

    override fun createLayout(context: Context): NativeAdView {
        return LayoutInflater.from(context)
            .inflate(layoutResId, null) as NativeAdView
    }

    override fun bindData(adView: NativeAdView, nativeAd: NativeAd) {
        val titleView = adView.findViewById<TextView>(R.id.tv_ad_title)
        val ctaButton = adView.findViewById<TextView>(R.id.btn_ad_cta)
        val iconView = adView.findViewById<ImageView>(R.id.iv_ad_icon)
        val iconContainer = adView.findViewById<View>(R.id.iconCv)
        val descView = adView.findViewById<TextView>(R.id.tv_ad_description)

        titleView.text = nativeAd.headline

        val body = nativeAd.body
        descView.text = body
        descView.visibility = if (body.isNullOrBlank()) View.GONE else View.VISIBLE

        val callToAction = nativeAd.callToAction
        ctaButton.text = callToAction
        ctaButton.visibility = if (callToAction.isNullOrBlank()) View.INVISIBLE else View.VISIBLE

        val icon = nativeAd.icon
        if (icon?.drawable != null) {
            iconView.setImageDrawable(icon.drawable)
            iconContainer.visibility = View.VISIBLE
        } else {
            iconView.setImageDrawable(null)
            iconContainer.visibility = View.GONE
        }

        adView.headlineView = titleView
        adView.callToActionView = ctaButton
        adView.iconView = iconView
        adView.bodyView = descView
        adView.advertiserView = null
        adView.priceView = null
        adView.storeView = null
        adView.setNativeAd(nativeAd)
    }
}
