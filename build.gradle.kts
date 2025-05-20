import org.apache.commons.io.output.ByteArrayOutputStream

// For `versionCode` we just use the number of commits.
val projectVersionCode: Int by extra {
    val stdout = ByteArrayOutputStream()
    rootProject.exec {
        commandLine("git", "rev-list", "HEAD", "--count")
        standardOutput = stdout
    }
    @Suppress("DEPRECATION") // toString() is deprecated.
    stdout.toString().trim().toInt()
}

// For versionName, we use the output of: 'git describe --tags --dirty' and replace
// all but the first '-' with a '.' character. This ensures our version string is
// compliant with Semantic Versioning, see https://semver.org/
//
// This yields version strings such as
//
//  "0.91.0"                     - for the release tagged 0.91.0
//  "0.91.0-42.g12345678"        - for 42 commits past release 0.91.0
//  "0.91.0-42.g12345678.dirty"  - for 42 commits past release 0.91.0 with local modifications
//
val projectVersionName: String by extra {
    val stdout = ByteArrayOutputStream()
    rootProject.exec {
        commandLine(
            "bash",
            "-c",
            "git describe --tags --dirty | sed 's/-/@temp_dash@/1; s/-/./g; s/@temp_dash@/-/g'"
        )
        standardOutput = stdout
    }
    @Suppress("DEPRECATION") // toString() is deprecated.
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
    alias(libs.plugins.kotlinCocoapods) apply false
}
