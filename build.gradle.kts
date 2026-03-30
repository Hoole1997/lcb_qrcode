plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.google.ksp) apply false
    alias(libs.plugins.google.gms.google.services) apply false
    alias(libs.plugins.google.firebase.crashlytics) apply false
    alias(libs.plugins.google.firebase.perf) apply false
}

val taskNames = gradle.startParameter.taskNames
val selectedChannel = when {
    taskNames.any { it.contains("Google", ignoreCase = true) } &&
        !taskNames.any { it.contains("Local", ignoreCase = true) } -> "google"
    taskNames.any { it.contains("Local", ignoreCase = true) } &&
        !taskNames.any { it.contains("Google", ignoreCase = true) } -> "local"
    else -> "local"
}

apply(from = file("app/src/$selectedChannel/config.gradle.kts"))
extra["selectedChannel"] = selectedChannel

subprojects {
    configurations.configureEach {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.jetbrains.kotlin" && requested.name == "kotlin-android-extensions-runtime") {
                useTarget("org.jetbrains.kotlin:kotlin-parcelize-runtime:${libs.versions.kotlin.get()}")
            }
        }
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
