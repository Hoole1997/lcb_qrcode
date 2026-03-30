package com.android.common.scanner.util

import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.android.common.bill.ads.AdResult
import com.android.common.bill.ads.ext.AdShowExt
import com.android.common.bill.ui.NativeAdStyleType
import kotlinx.coroutines.launch

fun FragmentActivity.loadNative(container: ViewGroup,
                                styleType: NativeAdStyleType = NativeAdStyleType.STANDARD,
                                condition: () -> Boolean = { true },
                                call: (Boolean) -> Unit = {}
) {
    lifecycleScope.launch {
        try {
            // 检查条件是否满足
            if (!condition.invoke()) {
                container.visibility = View.GONE
                call.invoke(false)
                return@launch
            }

            NativeAdStyleRegistry.update(container.context, styleType)

            val success = AdShowExt.showNativeAdInContainer(
                context = container.context,
                container = container,
                styleType = styleType
            )

            if (success) {
                container.visibility = View.VISIBLE
                call.invoke(true)
            } else {
                container.visibility = View.GONE
                call.invoke(false)
            }
        } catch (e: Exception) {
            container.visibility = View.GONE
            call.invoke(false)
        }
    }
}

fun FragmentActivity.loadInterstitial(condition: () -> Boolean = { true }, call: (Boolean) -> Unit) {
    lifecycleScope.launch {
        try {
            // 检查条件是否满足
            if (!condition.invoke()) {
                call.invoke(false)
                return@launch
            }

            when (val result = AdShowExt.showInterstitialAd(this@loadInterstitial)) {
                is AdResult.Success -> {
                    call.invoke(true)
                }

                is AdResult.Failure -> {
                    call.invoke(false)
                }
            }

        } catch (e: Exception) {
            call.invoke(false)
        }
    }
}
