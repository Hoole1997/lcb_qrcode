package com.android.common.bill.ads

import android.app.Activity
import android.app.Application
import android.graphics.Color
import android.os.Bundle
import android.view.View
import com.android.common.bill.ads.log.AdLogger

/**
 * AdActivity 拦截器
 * 用于拦截 Google AdMob 的 AdActivity 并设置隐藏导航栏
 */
class AdActivityInterceptor private constructor() {
    
    companion object {
        private var INSTANCE: AdActivityInterceptor? = null
        
        fun getInstance(): AdActivityInterceptor {
            return INSTANCE ?: AdActivityInterceptor().also { INSTANCE = it }
        }
    }
    
    private var isRegistered = false
    
    /**
     * 注册到 Application 中
     */
    fun register(application: Application) {
        if (isRegistered) {
            AdLogger.d("AdActivityInterceptor 已经注册")
            return
        }
        
        try {
            application.registerActivityLifecycleCallbacks(activityLifecycleCallbacks)
            isRegistered = true
            AdLogger.d("AdActivityInterceptor 注册成功")
        } catch (e: Exception) {
            AdLogger.e("AdActivityInterceptor 注册失败", e)
        }
    }
    
    /**
     * 注销注册
     */
    fun unregister(application: Application) {
        if (!isRegistered) {
            return
        }
        
        try {
            application.unregisterActivityLifecycleCallbacks(activityLifecycleCallbacks)
            isRegistered = false
            AdLogger.d("AdActivityInterceptor 注销成功")
        } catch (e: Exception) {
            AdLogger.e("AdActivityInterceptor 注销失败", e)
        }
    }
    
    /**
     * Activity 生命周期回调
     */
    private val activityLifecycleCallbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            // 检查是否是AdActivity
            if (isAdActivity(activity)) {
                AdLogger.d("检测到AdActivity创建: ${activity.javaClass.simpleName}")
                activity.window.apply {
                    addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)

                    @Suppress("DEPRECATION")
                    navigationBarColor = Color.TRANSPARENT
                }
            }
        }

        override fun onActivityStarted(activity: Activity) {
        }

        override fun onActivityResumed(activity: Activity) {
        }

        override fun onActivityPaused(activity: Activity) {}
        override fun onActivityStopped(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        override fun onActivityDestroyed(activity: Activity) {}
    }
    
    /**
     * 检查Activity是否是AdActivity
     * 使用类名判断，避免混淆影响
     */
    private fun isAdActivity(activity: Activity): Boolean {
        // 使用类名判断，即使混淆后也能通过特征识别
        val className = activity.javaClass.name
        
        // 排除我们自己的广告Activity
        if (className.contains("AdmobFullScreenNativeAdActivity") ||
            className.contains("com.remax.pr.ui.admob.AdmobFullScreenNativeAdActivity")) {
            return false
        }
        
        // 检查是否是 Google AdMob 的 AdActivity 或包含广告相关特征
        return className.contains("com.google.android.gms.ads") ||
               className.contains("AdActivity") || 
               className.contains("ads") || 
               className.contains("ad")
    }

} 
