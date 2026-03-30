package com.lcb.qrcode.ad

import android.content.Context
import com.android.common.bill.BillConfig
import com.android.common.bill.ui.NativeAdStyleType
import com.android.common.scanner.util.NativeAdStyleRegistry
import com.lcb.qrcode.R

object NativeAdLayoutResolver {

    fun admob(context: Context): Int = resolve(
        context = context,
        standardLayoutResId = BillConfig.admob.nativeStyleStandard.layoutResId,
        largeLayoutResId = BillConfig.admob.nativeStyleLarge.layoutResId,
        defaultStandardLayoutResId = R.layout.layout_native_ads,
        defaultLargeLayoutResId = R.layout.layout_native_ad_card
    )

    fun pangle(context: Context): Int = resolve(
        context = context,
        standardLayoutResId = BillConfig.pangle.nativeStyleStandard.layoutResId,
        largeLayoutResId = BillConfig.pangle.nativeStyleLarge.layoutResId,
        defaultStandardLayoutResId = R.layout.layout_pangle_native_ads,
        defaultLargeLayoutResId = R.layout.layout_pangle_native_ads_large
    )

    fun topon(context: Context): Int = resolve(
        context = context,
        standardLayoutResId = BillConfig.topon.nativeStyleStandard.layoutResId,
        largeLayoutResId = BillConfig.topon.nativeStyleLarge.layoutResId,
        defaultStandardLayoutResId = R.layout.layout_topon_native_ads,
        defaultLargeLayoutResId = R.layout.layout_topon_native_ads_large
    )

    private fun resolve(
        context: Context,
        standardLayoutResId: Int,
        largeLayoutResId: Int,
        defaultStandardLayoutResId: Int,
        defaultLargeLayoutResId: Int
    ): Int {
        return when (NativeAdStyleRegistry.resolve(context)) {
            NativeAdStyleType.LARGE -> largeLayoutResId.takeIf { it != 0 } ?: defaultLargeLayoutResId
            NativeAdStyleType.STANDARD -> standardLayoutResId.takeIf { it != 0 } ?: defaultStandardLayoutResId
        }
    }
}
