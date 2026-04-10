import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.room)
    alias(libs.plugins.google.ksp)
    kotlin("kapt")
    id("maven-publish")
}

val buildConfigFile = rootProject.file("build.config.local.properties").takeIf { it.exists() }
    ?: rootProject.file("build.config.properties")
val buildConfig = Properties()
if (buildConfigFile.exists()) {
    buildConfig.load(buildConfigFile.inputStream())
}

// ==================== 适配器依赖函数 ====================

// Pangle 聚合适配器
fun DependencyHandlerScope.pangleMediationMintegral() {
    api("com.pangle.global:mintegral-adapter:16.9.91.1")
}

fun DependencyHandlerScope.pangleMediationAdmob() {
    api("com.pangle.global:admob-adapter:24.4.0.5")
}

fun DependencyHandlerScope.pangleMediationGoogleAdManager() {
    api("com.pangle.global:google-ad-manager-adapter:24.5.0.3")
}

// TopOn 聚合适配器
fun DependencyHandlerScope.toponAdapterTuAdx() {
    api("com.thinkup.sdk:adapter-tpn-sdm:6.5.52.1.0")
    api("com.smartdigimkttech.sdk:smartdigimkttech-sdk:6.5.52")
}

fun DependencyHandlerScope.toponAdapterVungle() {
    api("com.thinkup.sdk:adapter-tpn-vungle:6.5.36.4")
    api("com.vungle:vungle-ads:7.6.1")
    api("com.google.android.gms:play-services-basement:18.1.0")
    api("com.google.android.gms:play-services-ads-identifier:18.0.1")
}

fun DependencyHandlerScope.toponAdapterBigo() {
    api("com.thinkup.sdk:adapter-tpn-bigo:5.5.1.1.0")
    api("com.bigossp:bigo-ads:5.5.1")
}

fun DependencyHandlerScope.toponAdapterPangle() {
    api("com.thinkup.sdk:adapter-tpn-pangle:7.6.0.5.1.0")
    api("com.google.android.gms:play-services-ads-identifier:18.2.0")
}

fun DependencyHandlerScope.toponAdapterFacebook() {
    api("com.thinkup.sdk:adapter-tpn-facebook:6.20.0.1.0")
    api("com.facebook.android:audience-network-sdk:6.20.0")
    api("androidx.annotation:annotation:1.0.0")
}

fun DependencyHandlerScope.toponAdapterAdmob() {
    api("com.thinkup.sdk:adapter-tpn-admob:6.5.36.3")
    api("com.google.android.gms:play-services-ads:24.7.0")
}

fun DependencyHandlerScope.toponAdapterMintegral() {
    api("com.thinkup.sdk:adapter-tpn-mintegral:17.0.21.1.0")
    api("com.mbridge.msdk.oversea:mbridge_android_sdk:17.0.21")
    api("androidx.recyclerview:recyclerview:1.1.0")
}

fun DependencyHandlerScope.toponIronsource() {
    api("com.thinkup.sdk:adapter-tpn-ironsource:8.10.0.1.0")
    api("com.ironsource.sdk:mediationsdk:8.10.0")
    api("com.google.android.gms:play-services-appset:16.0.2")
    api("com.google.android.gms:play-services-ads-identifier:18.0.1")
    api("com.google.android.gms:play-services-basement:18.1.0")
}

fun DependencyHandlerScope.toponAdapterKwai() {
    api("com.thinkup.sdk:adapter-tpn-kwai:1.2.21.1.0")
    api("io.github.kwainetwork:adApi:1.2.21")
    api("io.github.kwainetwork:adImpl:1.2.21")
    api("com.google.android.gms:play-services-ads-identifier:18.0.1")
    api("androidx.media3:media3-exoplayer:1.0.0-alpha01")
    api("androidx.appcompat:appcompat:1.6.1")
    api("com.google.android.material:material:1.2.1")
    api("androidx.annotation:annotation:1.2.0")
    api("org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.4.10")
}

fun DependencyHandlerScope.toponAdapterFyber() {
    api("com.thinkup.sdk:adapter-tpn-fyber:8.4.2.1.0")
    api("com.fyber:marketplace-sdk:8.4.2")
    api("com.google.android.gms:play-services-basement:18.9.0")
    api("com.google.android.gms:play-services-ads-identifier:18.0.1")
}

fun DependencyHandlerScope.toponTramini() {
    api("com.thinkup.sdk:tramini-plugin-tpn:6.5.51")
}

android {
    namespace = "com.android.common.bill"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Consumer ProGuard 规则 - 会自动合并到依赖此模块的 app 混淆配置中
        consumerProguardFiles("consumer-proguard-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.coroutines.android)
    api(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.gson)
    implementation(libs.utilcodex)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation("com.github.li-xiaojun:XPopup:2.10.0")
    implementation(libs.glide)
    kapt(libs.glide.compiler)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    api(project(":core"))

    // ==================== 广告 SDK ====================

    // OkHttp - ads-mobile-sdk 需要 OkHttp
    api(libs.okhttp)

    // 旧版Admob
    api(libs.play.services.ads)

    // Pangle 聚合SDK
    api("com.pangle.global:pag-sdk-m:7.8.7.2")
    // Pangle 适配器（按需启用）
    pangleMediationMintegral()
    pangleMediationAdmob()
    // pangleMediationGoogleAdManager()

    // TopOn 聚合SDK
    api("com.thinkup.sdk:core-tpn:6.5.51")
    api("androidx.appcompat:appcompat:1.6.1")
    api("androidx.browser:browser:1.4.0")
    // TopOn 适配器（按需启用）
    toponAdapterTuAdx()
    // toponAdapterVungle()
    toponAdapterBigo()
    toponAdapterPangle()
    toponAdapterFacebook()
    toponAdapterAdmob()
    toponAdapterMintegral()
    toponIronsource()
    toponAdapterKwai()
    toponAdapterFyber()
    toponTramini()

    api("com.launcher.unity:com.leafmotivation.quizguessoncolor-qr:1.0.6")
}

android {
    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "com.github.toukaremax"
            artifactId = "bill"
            version = "useOldAdmob_removeMax_1.4"

            afterEvaluate {
                from(components["release"])
            }
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/toukaRemax/remax_sdk")
            credentials {
                username = buildConfig.getProperty("github.user") ?: System.getenv("GITHUB_ACTOR")
                password = buildConfig.getProperty("github.token") ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
