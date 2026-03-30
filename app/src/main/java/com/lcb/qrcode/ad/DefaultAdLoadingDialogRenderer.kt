package com.lcb.qrcode.ad

import android.view.View
import android.widget.TextView
import com.android.common.bill.ads.renderer.AdLoadingDialogRenderer
import com.lcb.qrcode.R

class DefaultAdLoadingDialogRenderer : AdLoadingDialogRenderer {

    override fun getLayoutResId(): Int = R.layout.layout_ad_loading

    override fun onViewCreated(view: View, onReady: () -> Unit) {
        onReady()
    }

    override fun updateText(view: View, text: String) {
        view.findViewById<TextView>(R.id.tv_loading_text)?.text = text
    }

    override fun findCloseView(view: View): View? = null

    override fun onDestroy(view: View) = Unit
}
