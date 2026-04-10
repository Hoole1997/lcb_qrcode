package com.android.common.bill.ads.util

import android.app.Activity
import com.blankj.utilcode.util.ActivityUtils
import com.bytedance.sdk.openadsdk.activity.TTAppOpenAdActivity
import com.bytedance.sdk.openadsdk.activity.TTFullScreenExpressVideoActivity
import com.bytedance.sdk.openadsdk.activity.TTRewardExpressVideoActivity
import com.google.android.gms.ads.AdActivity
import com.android.common.bill.ui.admob.AdmobFullScreenNativeAdActivity
import com.android.common.bill.ui.pangle.PangleFullScreenNativeAdActivity

object PositionGet {
    fun get(): String{
        val activityList: MutableList<Activity?> = ActivityUtils.getActivityList()
        for (activity in activityList) {
            if (activity == null || !ActivityUtils.isActivityAlive(activity)
                || activity is AdActivity || activity is AdmobFullScreenNativeAdActivity
                || activity is PangleFullScreenNativeAdActivity || activity is TTRewardExpressVideoActivity || activity is TTAppOpenAdActivity || activity is TTFullScreenExpressVideoActivity
                ) {
                continue
            }
            return activity::class.simpleName.orEmpty()
        }
        return ""
    }
}
