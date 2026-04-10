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

# ==================== Gson 混淆规则 ====================

-dontwarn com.google.gson.**
-keep class com.google.gson.** { *; }

-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.Expose <fields>;
    @com.google.gson.annotations.Expose <methods>;
}

-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# ==================== Firebase 混淆规则 ====================

-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
-keep class com.google.firebase.analytics.** { *; }
-keep class com.google.android.gms.measurement.** { *; }
-keep class com.google.firebase.remoteconfig.** { *; }
-keep class com.google.firebase.FirebaseApp { *; }
-keep class com.google.firebase.FirebaseOptions { *; }

# ==================== 通用保护规则 ====================

-keepclasseswithmembernames class * {
    native <methods>;
}

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keep interface * {
    *;
}

-keep public class * extends java.lang.Exception { *; }
-keep class * implements java.io.Serializable { *; }

-keepclassmembers class **.BuildConfig {
    public static <fields>;
}

-dontwarn java.lang.invoke.StringConcatFactory
-dontwarn java.lang.invoke.MethodHandles
-dontwarn java.lang.invoke.MethodHandles$Lookup

-dontwarn **
-ignorewarnings
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5
-allowaccessmodification
-dontpreverify
