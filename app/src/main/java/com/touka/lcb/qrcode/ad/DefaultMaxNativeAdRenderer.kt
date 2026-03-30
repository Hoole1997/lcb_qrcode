package com.touka.lcb.qrcode.ad

import android.content.Context
import com.android.common.bill.ads.renderer.MaxNativeAdRenderer
import com.applovin.mediation.nativeAds.MaxNativeAdView
import com.applovin.mediation.nativeAds.MaxNativeAdViewBinder
import com.touka.lcb.qrcode.R

/**
 * MAX 原生广告默认渲染器
 */
class DefaultMaxNativeAdRenderer(
    private val layoutResId: Int = R.layout.layout_max_native_ads
) : MaxNativeAdRenderer {

    override fun createNativeAdView(context: Context): MaxNativeAdView {
        val binder = MaxNativeAdViewBinder.Builder(layoutResId)
            .setTitleTextViewId(R.id.tv_ad_title)
            .setBodyTextViewId(R.id.tv_ad_description)
            .setIconImageViewId(R.id.iv_ad_icon)
            .setCallToActionButtonId(R.id.btn_ad_cta)
            .setOptionsContentViewGroupId(R.id.fl_ad_options)
            .build()

        return MaxNativeAdView(binder, context)
    }
}
