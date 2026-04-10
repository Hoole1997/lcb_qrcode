# ==================== Bill 模块 Consumer ProGuard 规则 ====================
# 此文件中的规则会自动合并到依赖此模块的 app 混淆配置中
# 包含所有广告聚合平台和适配器的官方混淆规则

# ==================== 通用保护规则 ====================

-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes Exceptions

# ==================== Google Play Services & AdMob ====================
# 官方文档: https://developers.google.com/admob/android/quick-start

-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

-keep class com.google.android.gms.ads.** { *; }
-keep class com.google.android.gms.ads.identifier.** { *; }
-keep class com.google.android.gms.ads.mediation.** { *; }
-keep class com.google.android.gms.measurement.** { *; }

# Google UMP (User Messaging Platform)
-keep class com.google.android.ump.** { *; }
-dontwarn com.google.android.ump.**

# ==================== Pangle SDK ====================
# 官方文档: https://www.pangleglobal.com/integration/integrate-pangle-sdk-for-android

-keep class com.bytedance.sdk.** { *; }
-dontwarn com.bytedance.sdk.**

-keep class com.bytedance.sdk.openadsdk.** { *; }
-keep class com.pangle.sdk.** { *; }
-dontwarn com.pangle.sdk.**

# ==================== TopOn SDK ====================
# 官方文档: https://help.toponad.net/docs/integration

-keep class com.thinkup.** { *; }
-dontwarn com.thinkup.**

-keep class com.anythink.** { *; }
-dontwarn com.anythink.**

# TopOn Network Adapters
-keep class com.anythink.network.** { *; }

# ==================== Mintegral SDK ====================
# 官方文档: https://github.com/Mintegral-official/mediation-android

-keepattributes Signature
-keepattributes *Annotation*

-keep class com.mbridge.** { *; }
-keep interface com.mbridge.** { *; }
-dontwarn com.mbridge.**
-keepclassmembers class **.R$* { public static final int mbridge*; }

-keep public class com.mbridge.* extends androidx.** { *; }
-keep public class androidx.viewpager.widget.PagerAdapter { *; }
-keep public class androidx.viewpager.widget.ViewPager.OnPageChangeListener { *; }
-keep interface androidx.annotation.IntDef { *; }
-keep interface androidx.annotation.Nullable { *; }
-keep interface androidx.annotation.CheckResult { *; }
-keep interface androidx.annotation.NonNull { *; }
-keep public class androidx.fragment.app.Fragment { *; }
-keep public class androidx.core.content.FileProvider { *; }
-keep public class androidx.core.app.NotificationCompat { *; }
-keep public class androidx.appcompat.widget.AppCompatImageView { *; }
-keep public class androidx.recyclerview.* { *; }
-keep class com.mbridge.msdk.foundation.tools.FastKV { *; }
-keep class com.mbridge.msdk.foundation.tools.FastKV$Builder { *; }

# ==================== Vungle/Liftoff SDK ====================
# 官方文档: https://support.vungle.com/hc/en-us/articles/360047780372

-keep class com.vungle.** { *; }
-dontwarn com.vungle.**

-keep class com.vungle.ads.** { *; }
-keep class com.vungle.warren.** { *; }

# Vungle 依赖
-dontwarn com.vungle.warren.error.VungleException$ExceptionCode
-keep class com.moat.** { *; }
-dontwarn com.moat.**

# ==================== Bigo Ads SDK ====================
# 官方文档: https://www.bigossp.com/guide/sdk/android

-keep class com.bigossp.** { *; }
-dontwarn com.bigossp.**

-keep class com.bigo.** { *; }
-dontwarn com.bigo.**

# ==================== Facebook Audience Network ====================
# 官方文档: https://developers.facebook.com/docs/audience-network

-keep class com.facebook.ads.** { *; }
-dontwarn com.facebook.ads.**

-keep class com.facebook.** { *; }
-dontwarn com.facebook.**

-keepclassmembers class * implements java.io.Serializable {
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ==================== TU ADX / SmartDigiMktTech SDK ====================

-keep class com.smartdigimkttech.** { *; }
-dontwarn com.smartdigimkttech.**

# ==================== IronSource SDK ====================
# 官方文档: https://developers.is.com/ironsource-mobile/android/android-sdk/

-keepclassmembers class com.ironsource.sdk.controller.IronSourceWebView$JSInterface {
    public *;
}
-keep class com.ironsource.adapters.** { *; }
-keep class com.ironsource.sdk.** { *; }
-keep class com.ironsource.** { *; }
-dontwarn com.ironsource.**

# ==================== TopOn Tramini 反作弊插件 ====================

-keep class com.anythink.tramini.** { *; }
-dontwarn com.anythink.tramini.**

# ==================== 其他依赖 ====================

# Gson
-keep class com.google.gson.** { *; }
-dontwarn com.google.gson.**
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# Protobuf
-dontwarn com.google.protobuf.**
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }

# ExoPlayer (视频广告可能用到)
-keep class com.google.android.exoplayer2.** { *; }
-dontwarn com.google.android.exoplayer2.**

# WebView JavaScript Interface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# 保留所有广告相关的回调接口
-keep interface * {
    *;
}

# ==================== Kotlin Data Class 混淆规则 ====================

# 保护 Kotlin data class 的构造函数和组件函数
-keepclassmembers class * {
    <init>(...);
}

# 保护 data class 的 componentN 函数
-keepclassmembers class * {
    public ** component*();
}

# 保护 data class 的 copy 函数
-keepclassmembers class * {
    public ** copy(...);
}

# ==================== Kotlin 属性委托混淆规则 ====================

# 保护 Kotlin 属性委托相关类
-keep class kotlin.reflect.KProperty { *; }
-keep class kotlin.reflect.KProperty$* { *; }
-keep class kotlin.properties.ReadWriteProperty { *; }

# ==================== Kotlin 协程混淆规则 ====================

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
