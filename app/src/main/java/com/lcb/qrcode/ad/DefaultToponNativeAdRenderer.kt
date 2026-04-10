package com.lcb.qrcode.ad

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.android.common.bill.ads.renderer.ToponNativeAdRenderer
import com.bumptech.glide.Glide
import com.android.common.bill.ui.topon.ToponNativeAdStyle
import com.lcb.qrcode.R
import com.thinkup.nativead.api.TUNativeMaterial
import com.thinkup.nativead.api.TUNativePrepareInfo

/**
 * TopOn 原生广告默认渲染器
 */
class DefaultToponNativeAdRenderer(
    private val layoutResId: Int = R.layout.layout_topon_native_ads
) : ToponNativeAdRenderer {

    override fun createLayout(context: Context): ViewGroup {
        return LayoutInflater.from(context)
            .inflate(layoutResId, null) as ViewGroup
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
    }

    override fun createPrepareInfo(adView: ViewGroup): TUNativePrepareInfo {
        val prepareInfo = TUNativePrepareInfo()
        prepareInfo.closeView = null

        adView.findViewById<TextView>(R.id.tv_ad_title)?.let {
            prepareInfo.clickViewList.add(it)
            prepareInfo.setTitleView(it)
        }
        adView.findViewById<TextView>(R.id.tv_ad_description)?.let {
            prepareInfo.clickViewList.add(it)
            prepareInfo.descView = it
        }
        adView.findViewById<TextView>(R.id.btn_ad_cta)?.let {
            prepareInfo.clickViewList.add(it)
            prepareInfo.ctaView = it
        }
        adView.findViewById<ImageView>(R.id.iv_ad_icon)?.let {
            prepareInfo.clickViewList.add(it)
            prepareInfo.setIconView(it)
        }

        return prepareInfo
    }
}
