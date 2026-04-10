package com.android.common.bill.ads.util

import android.app.Activity
import androidx.fragment.app.FragmentActivity
import com.android.common.bill.ads.ext.AdShowExt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Java 调用 InterstitialAds 的辅助类
 */
object InterstitialAdsHelper {

    /**
     * 从 Java 代码展示插页广告
     * @param activity Activity 上下文
     */
    @JvmStatic
    fun showInterstitial(activity: FragmentActivity) {
        CoroutineScope(Dispatchers.Main).launch {
            AdShowExt.showInterstitialAd(activity)
        }
    }
}
