plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

@Suppress("UNCHECKED_CAST")
fun configMap(name: String): Map<String, Any?> =
    rootProject.extra[name] as? Map<String, Any?>
        ?: error("Missing '$name' config. Expected root build script to load app/src/<variant>/config.gradle.kts")

fun Map<String, Any?>.stringValue(name: String): String = this[name] as? String ?: ""

private val analyticsConfig = configMap("analytics")

android {
    namespace = "net.corekit.metrics"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        targetSdk = 35
        buildConfigField("String", "ADJUST_APP_TOKEN", "\"${analyticsConfig.stringValue("adjustAppToken")}\"")
        buildConfigField("String", "THINKING_DATA_APP_ID", "\"${analyticsConfig.stringValue("thinkingDataAppId")}\"")
        buildConfigField("String", "THINKING_DATA_SERVER_URL", "\"${analyticsConfig.stringValue("thinkingDataServerUrl")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    kotlinOptions {
        jvmTarget = "17"
    }
    
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    compileOnly(libs.remax.core)
    implementation(libs.androidx.core.ktx)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.config)
    implementation(libs.gson)
    implementation(libs.kotlinx.coroutines.android)
    
    // Adjust SDK
    api("com.adjust.sdk:adjust-android:5.4.3")
    api("com.android.installreferrer:installreferrer:2.2")
    api("com.google.android.gms:play-services-ads-identifier:18.0.1")
    
    // ThinkingData SDK
    api("cn.thinkingdata.android:ThinkingAnalyticsSDK:3.0.2")
}
