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
class DefaultAdmobNativeAdRenderer : AdmobNativeAdRenderer {

    override fun createLayout(context: Context, style: NativeAdStyle): NativeAdView {
        return LayoutInflater.from(context)
            .inflate(style.layoutResId, null) as NativeAdView
    }

    override fun bindData(adView: NativeAdView, nativeAd: NativeAd) {
        val titleView = adView.findViewById<TextView>(R.id.tv_ad_title)
        val ctaButton = adView.findViewById<TextView>(R.id.btn_ad_cta)
        val iconView = adView.findViewById<ImageView>(R.id.iv_ad_icon)
        val descView = adView.findViewById<TextView>(R.id.tv_ad_description)

        titleView?.text = nativeAd.headline ?: "Test Google Ads"
        ctaButton?.text = nativeAd.callToAction ?: "INSTALL"
        descView?.text = nativeAd.body

        nativeAd.icon?.let { icon ->
            iconView?.setImageDrawable(icon.drawable)
            iconView?.visibility = View.VISIBLE
        } ?: run {
            iconView?.setImageResource(android.R.drawable.ic_menu_info_details)
            iconView?.visibility = View.VISIBLE
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
