# ==================== 通用混淆规则 ====================

# 保留行号信息，用于调试堆栈跟踪
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# 保留注解
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes Exceptions
-keepattributes LocalVariableTable
-keepattributes MethodParameters

# ==================== Android 基础组件保护 ====================

# 保留所有 Activity
-keep public class * extends android.app.Activity { *; }
-keep public class * extends androidx.activity.ComponentActivity { *; }

# 保留所有 Fragment
-keep public class * extends android.app.Fragment { *; }
-keep public class * extends androidx.fragment.app.Fragment { *; }

# 保留所有 Service
-keep public class * extends android.app.Service { *; }

# 保留所有 BroadcastReceiver
-keep public class * extends android.content.BroadcastReceiver { *; }

# 保留所有 ContentProvider
-keep public class * extends android.content.ContentProvider { *; }

# 保留所有 Application
-keep public class * extends android.app.Application { *; }

# ==================== ViewBinding 和 DataBinding ====================

# ViewBinding
-keep class * extends androidx.viewbinding.ViewBinding {
    public static *** inflate(android.view.LayoutInflater);
    public static *** inflate(android.view.LayoutInflater, android.view.ViewGroup, boolean);
    public static *** bind(android.view.View);
}

# DataBinding
-keep class androidx.databinding.** { *; }
-dontwarn androidx.databinding.**

# ==================== Jetpack Compose 混淆规则 ====================

# Compose 相关
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Compose 注解
-keep @androidx.compose.runtime.Stable class *
-keep @androidx.compose.runtime.Immutable class *
-keep @androidx.compose.runtime.Composable class *

# Compose 预览
-keep @androidx.compose.ui.tooling.preview.Preview class *


# ==================== Kotlin 协程混淆规则 ====================

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ==================== WorkManager 混淆规则 ====================

# WorkManager
-keep class androidx.work.** { *; }
-keep class androidx.startup.** { *; }
-dontwarn androidx.work.**

# 保留 WorkManager 的 Worker 类
-keep class * extends androidx.work.Worker {
    public <init>(...);
    public androidx.work.ListenableWorker$Result doWork();
}

# ==================== Kotlin 属性委托混淆规则 ====================

# 保护 Kotlin 属性委托相关类
-keep class kotlin.reflect.KProperty { *; }
-keep class kotlin.reflect.KProperty$* { *; }
-keep class kotlin.properties.ReadWriteProperty { *; }

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

# ==================== Gson 混淆规则 ====================

# Gson 使用泛型和注解
-keepattributes Signature
-keepattributes *Annotation*

# Gson 类
-dontwarn com.google.gson.**
-keep class com.google.gson.** { *; }

# 保留所有使用 @SerializedName 注解的字段
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# 保留所有使用 @Expose 注解的字段和方法
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.Expose <fields>;
    @com.google.gson.annotations.Expose <methods>;
}

# Gson 类型适配器
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# ==================== Google AdMob 混淆规则 ====================

# Google Play Services Ads
-keep class com.google.android.gms.ads.** { *; }
-dontwarn com.google.android.gms.ads.**

# 保留 AdMob 相关的类
-keep class com.google.android.gms.ads.identifier.** { *; }
-keep class com.google.android.gms.ads.mediation.** { *; }
-keep class com.google.android.gms.ads.formats.** { *; }

# 保留 AdMob 的广告加载器
-keep class com.google.android.gms.ads.AdLoader { *; }
-keep class com.google.android.gms.ads.AdRequest { *; }

# ==================== Firebase 混淆规则 ====================

# Firebase
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# Firebase Analytics
-keep class com.google.firebase.analytics.** { *; }
-keep class com.google.android.gms.measurement.** { *; }

# Firebase Remote Config
-keep class com.google.firebase.remoteconfig.** { *; }

# Firebase 配置
-keep class com.google.firebase.FirebaseApp { *; }
-keep class com.google.firebase.FirebaseOptions { *; }

# ==================== Adjust SDK 混淆规则 ====================

# Adjust SDK
-keep class com.adjust.sdk.** { *; }
-dontwarn com.adjust.sdk.**

# ==================== ReMax SDK 公共 API 保护 ====================

# 保护 Analytics 模块的公共 API
-keep class com.remax.analytics.log.AnalyticsLogger { *; }

# ==================== 反射相关 ====================

# 保留使用反射的类和方法
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# 保留 Parcelable 实现
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# ==================== 通用保护规则 ====================

# 保留所有 native 方法
-keepclasseswithmembernames class * {
    native <methods>;
}

# 保留所有枚举类
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# 保留所有接口
-keep interface * {
    *;
}

# 保留所有内部类
-keepclassmembers class * {
    ** *$*;
}

# 保留所有自定义异常
-keep public class * extends java.lang.Exception { *; }

# 保留所有数据模型类
-keep class * implements java.io.Serializable { *; }


# ==================== BuildConfig 保护 ====================

# 保护 BuildConfig 中的所有字段
-keepclassmembers class **.BuildConfig {
    public static <fields>;
}

# ==================== 缺失类处理 ====================

# 处理 Kotlin 字符串拼接相关的缺失类
-dontwarn java.lang.invoke.StringConcatFactory
-dontwarn java.lang.invoke.MethodHandles
-dontwarn java.lang.invoke.MethodHandles$Lookup

# ==================== 优化规则 ====================

# 移除未使用的代码
-dontwarn **
-ignorewarnings

# 优化代码
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5
-allowaccessmodification
-dontpreverify