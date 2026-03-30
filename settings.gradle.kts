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

    val githubProps = Properties().apply {
        file("build.config.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
    }

    repositories {
        google()
        mavenCentral()
        maven(url = "https://jitpack.io")
        maven(url = "https://artifact.bytedance.com/repository/pangle/")
        maven(url = "https://dl-maven-android.mintegral.com/repository/mbridge_android_sdk_oversea")
        maven(url = "https://android-sdk.is.com/")
        maven(url = "https://jfrog.anythinktech.com/artifactory/overseas_sdk")
        maven(url = "https://artifacts.applovin.com/android")
        maven(url = "https://maven.pkg.github.com/toukaRemax/remax_sdk") {
            credentials {
                username = githubProps.getProperty("github.user")
                    ?: System.getenv("GITHUB_ACTOR")
                    ?: ""
                password = githubProps.getProperty("github.token")
                    ?: System.getenv("GITHUB_TOKEN")
                    ?: ""
            }
        }
    }
}

rootProject.name = "lcb_qrcode"

include(":app")
include(":scanner")
include(":metrics")
