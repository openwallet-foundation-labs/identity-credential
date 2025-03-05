import org.apache.commons.io.output.ByteArrayOutputStream

// For `versionCode` we just use the number of commits.
val projectVersionCode: Int by extra {
    val stdout = ByteArrayOutputStream()
    rootProject.exec {
        commandLine("git", "rev-list", "HEAD", "--count")
        standardOutput = stdout
    }
    @Suppress("DEPRECATION")
    stdout.toString().trim().toInt()
}

// For versionName, we use the output of: git describe --tags --dirty
val projectVersionName: String by extra {
    val stdout = ByteArrayOutputStream()
    rootProject.exec {
        commandLine("git", "describe", "--tags", "--dirty")
        standardOutput = stdout
    }
    @Suppress("DEPRECATION")
    stdout.toString().trim()
}

plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.jetbrainsCompose) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.jetbrains.kotlin.jvm) apply false
    alias(libs.plugins.gretty) apply false
    alias(libs.plugins.navigation.safe.args) apply false
    alias(libs.plugins.parcelable) apply false
    alias(libs.plugins.buildconfig) apply false
}
