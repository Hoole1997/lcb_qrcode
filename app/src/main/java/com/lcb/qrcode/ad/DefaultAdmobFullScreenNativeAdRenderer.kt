package com.lcb.qrcode.ad

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.LifecycleOwner
import com.android.common.bill.ads.renderer.AdmobFullScreenNativeAdRenderer
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.lcb.qrcode.R

/**
 * Admob 全屏原生广告默认渲染器
 */
class DefaultAdmobFullScreenNativeAdRenderer : AdmobFullScreenNativeAdRenderer {

    override fun createLayout(context: Context): NativeAdView {
        return LayoutInflater.from(context)
            .inflate(R.layout.layout_fullscreen_native_ad, null) as NativeAdView
    }

    override fun bindData(adView: NativeAdView, nativeAd: NativeAd, lifecycleOwner: LifecycleOwner) {
        val titleView = adView.findViewById<TextView>(R.id.tv_ad_title)
        val descView = adView.findViewById<TextView>(R.id.tv_ad_description)
        val ctaButton = adView.findViewById<TextView>(R.id.btn_ad_cta)
        val iconView = adView.findViewById<ImageView>(R.id.iv_ad_icon)
        val mediaView = adView.findViewById<MediaView>(R.id.mv_ad_media)

        titleView?.text = nativeAd.headline ?: "Test Google Ads"
        descView?.text = nativeAd.body ?: "Test Google Ads"
        ctaButton?.text = nativeAd.callToAction ?: "Open"

        nativeAd.icon?.let { icon ->
            iconView?.setImageDrawable(icon.drawable)
            iconView?.visibility = View.VISIBLE
        } ?: run {
            iconView?.setImageResource(android.R.drawable.ic_menu_info_details)
            iconView?.visibility = View.VISIBLE
        }

        nativeAd.mediaContent?.let { mediaContent ->
            mediaView?.mediaContent = mediaContent
            mediaView?.visibility = View.VISIBLE
        } ?: run {
            mediaView?.visibility = View.GONE
        }

        adView.headlineView = titleView
        adView.bodyView = descView
        adView.callToActionView = ctaButton
        adView.iconView = iconView
        adView.mediaView = mediaView
        adView.starRatingView = null
        adView.advertiserView = null
        adView.priceView = null
        adView.storeView = null

        adView.setNativeAd(nativeAd)
    }

    override fun createLoadingView(context: Context, container: ViewGroup) {
        container.removeAllViews()
        val loadingView = LayoutInflater.from(context)
            .inflate(R.layout.layout_fullscreen_loading, container, false)
        container.addView(loadingView)
    }
}
