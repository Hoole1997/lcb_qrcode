import java.util.Properties

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        google()
        mavenCentral()
        maven(url = "https://jitpack.io")
        maven(url = "https://artifact.bytedance.com/repository/pangle/")
        maven(url = "https://dl-maven-android.mintegral.com/repository/mbridge_android_sdk_oversea")
        maven(url = "https://android-sdk.is.com/")
        maven(url = "https://jfrog.anythinktech.com/artifactory/overseas_sdk")
        maven(url = "https://artifacts.applovin.com/android")
        maven("https://repo.dgtverse.cn/repository/maven-public")
    }
}

rootProject.name = "lcb_qrcode"

include(":app")
include(":scanner")
include(":metrics")
include(":bill")
include(":core")
