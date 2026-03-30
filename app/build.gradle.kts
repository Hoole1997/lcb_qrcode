plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.gms.google.services)
    alias(libs.plugins.google.firebase.crashlytics)
    alias(libs.plugins.google.firebase.perf)
}

@Suppress("UNCHECKED_CAST")
fun configMap(name: String): Map<String, Any?> =
    rootProject.extra[name] as? Map<String, Any?>
        ?: error("Missing '$name' config. Expected root build script to load app/src/<variant>/config.gradle.kts")

@Suppress("UNCHECKED_CAST")
fun Map<String, Any?>.nestedMap(name: String): Map<String, Any?> = this[name] as? Map<String, Any?> ?: emptyMap()

fun Map<String, Any?>.stringValue(name: String): String = this[name] as? String ?: ""

fun Map<String, Any?>.intValue(name: String, default: Int): Int = when (val value = this[name]) {
    is Int -> value
    is Number -> value.toInt()
    is String -> value.toIntOrNull() ?: default
    else -> default
}

val appConfig = configMap("app")
val analyticsConfig = configMap("analytics")
val adMobConfig = configMap("admob")
val adMobUnitConfig = adMobConfig.nestedMap("adUnitIds")
val pangleConfig = configMap("pangle")
val pangleUnitConfig = pangleConfig.nestedMap("adUnitIds")
val toponConfig = configMap("topon")
val toponUnitConfig = toponConfig.nestedMap("adUnitIds")

android {
    namespace = "com.lcb.qrcode"
    compileSdk = appConfig.intValue("compileSdk", 36)

    defaultConfig {
        applicationId = appConfig.stringValue("applicationId").ifBlank { "com.lcb.qrcode" }
        minSdk = appConfig.intValue("minSdk", 26)
        targetSdk = appConfig.intValue("targetSdk", 35)
        versionCode = appConfig.intValue("versionCode", 1)
        versionName = appConfig.stringValue("versionName").ifBlank { "1.0.0" }

        manifestPlaceholders["ADMOB_APPLICATION_ID"] = adMobConfig.stringValue("applicationId")

        buildConfigField("String", "DEFAULT_USER_CHANNEL", "\"${analyticsConfig.stringValue("defaultUserChannel")}\"")

        buildConfigField("String", "PRIVACY_POLICY_URL", "\"${appConfig.stringValue("privacyPolicyUrl")}\"")

        buildConfigField("String", "ADMOB_APPLICATION_ID", "\"${adMobConfig.stringValue("applicationId")}\"")
        buildConfigField("String", "ADMOB_BANNER_ID", "\"${adMobUnitConfig.stringValue("banner")}\"")
        buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"${adMobUnitConfig.stringValue("interstitial")}\"")
        buildConfigField("String", "ADMOB_SPLASH_ID", "\"${adMobUnitConfig.stringValue("splash")}\"")
        buildConfigField("String", "ADMOB_NATIVE_ID", "\"${adMobUnitConfig.stringValue("native")}\"")
        buildConfigField("String", "ADMOB_FULL_NATIVE_ID", "\"${adMobUnitConfig.stringValue("full_native")}\"")
        buildConfigField("String", "ADMOB_REWARDED_ID", "\"${adMobUnitConfig.stringValue("rewarded")}\"")

        buildConfigField("String", "PANGLE_APPLICATION_ID", "\"${pangleConfig.stringValue("applicationId")}\"")
        buildConfigField("String", "PANGLE_SPLASH_ID", "\"${pangleUnitConfig.stringValue("splash")}\"")
        buildConfigField("String", "PANGLE_BANNER_ID", "\"${pangleUnitConfig.stringValue("banner")}\"")
        buildConfigField("String", "PANGLE_INTERSTITIAL_ID", "\"${pangleUnitConfig.stringValue("interstitial")}\"")
        buildConfigField("String", "PANGLE_NATIVE_ID", "\"${pangleUnitConfig.stringValue("native")}\"")
        buildConfigField("String", "PANGLE_FULL_NATIVE_ID", "\"${pangleUnitConfig.stringValue("full_native")}\"")
        buildConfigField("String", "PANGLE_REWARDED_ID", "\"${pangleUnitConfig.stringValue("rewarded")}\"")

        buildConfigField("String", "TOPON_APPLICATION_ID", "\"${toponConfig.stringValue("applicationId")}\"")
        buildConfigField("String", "TOPON_APP_KEY", "\"${toponConfig.stringValue("appKey")}\"")
        buildConfigField("String", "TOPON_INTERSTITIAL_ID", "\"${toponUnitConfig.stringValue("interstitial")}\"")
        buildConfigField("String", "TOPON_REWARDED_ID", "\"${toponUnitConfig.stringValue("rewarded")}\"")
        buildConfigField("String", "TOPON_NATIVE_ID", "\"${toponUnitConfig.stringValue("native")}\"")
        buildConfigField("String", "TOPON_SPLASH_ID", "\"${toponUnitConfig.stringValue("splash")}\"")
        buildConfigField("String", "TOPON_FULL_NATIVE_ID", "\"${toponUnitConfig.stringValue("full_native")}\"")
        buildConfigField("String", "TOPON_BANNER_ID", "\"${toponUnitConfig.stringValue("banner")}\"")
    }

    flavorDimensions += "channel"

    productFlavors {
        create("local") {
            dimension = "channel"
        }
        create("google") {
            dimension = "channel"
        }
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
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

    packaging {
        resources {
            excludes += setOf(
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
            )
        }
    }
}

dependencies {
    implementation(project(":scanner"))
    implementation(project(":metrics"))
    implementation(libs.remax.core)
    implementation(libs.remax.bill)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.cardview)
    implementation(libs.material)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.config)
    implementation(libs.firebase.crashlytics.ndk)
    implementation(libs.firebase.perf)
    implementation(libs.gson)
    implementation("com.github.bumptech.glide:glide:4.16.0")
}
