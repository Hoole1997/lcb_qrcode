package com.touka.lcb.qrcode.ad

import android.content.Context
import com.android.common.bill.ads.renderer.MaxFullScreenNativeAdRenderer
import com.applovin.mediation.nativeAds.MaxNativeAdView
import com.applovin.mediation.nativeAds.MaxNativeAdViewBinder
import com.touka.lcb.qrcode.R

/**
 * MAX 全屏原生广告默认渲染器
 */
class DefaultMaxFullScreenNativeAdRenderer : MaxFullScreenNativeAdRenderer {

    override fun createNativeAdView(context: Context): MaxNativeAdView {
        val binder = MaxNativeAdViewBinder.Builder(R.layout.layout_max_full_screen_native_ads)
            .setTitleTextViewId(R.id.tv_ad_title)
            .setBodyTextViewId(R.id.tv_ad_description)
            .setIconImageViewId(R.id.iv_ad_icon)
            .setMediaContentViewGroupId(R.id.fl_media_container)
            .setCallToActionButtonId(R.id.btn_ad_cta)
            .setOptionsContentViewGroupId(R.id.fl_ad_options)
            .build()

        return MaxNativeAdView(binder, context)
    }
}
