rootProject.name = "MultipazProject"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

// As per https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-test.html#-o0tm8i_54
// "Currently, you cannot run common Compose Multiplatform tests using android (local) test
// configurations, so gutter icons in Android Studio, for example, won't be helpful."
//
// This is not a problem because the tests will get run as part of the multipaz-compose:connectedCheck
// tasks.
//
// When this starts working again, we can remove the lines below.
//
startParameter.excludedTaskNames +=
    listOf(
        ":multipaz-compose:testDebugUnitTest",
        ":multipaz-compose:testReleaseUnitTest"
    )

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        maven("https://jitpack.io") {
            mavenContent {
                includeGroup("com.github.yuriy-budiyev")
            }
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version("0.9.0")
}

include(":multipaz-cbor-rpc")
include(":multipaz")
include(":multipaz:SwiftBridge")
include(":multipaz-doctypes")
include(":multipaz-android-legacy")
include(":multipaz-provisioning")
include(":multipaz-provisioning-api")
include(":multipaz-models")
include(":multipaz-csa")
include(":multipaz-android-legacy")
include(":multipaz-zkp")
include(":multipazctl")
include(":multipaz-mrtd-reader")
include(":multipaz-mrtd-reader-android")
include(":multipaz-compose")
include(":jpeg2k")
include(":samples:testapp")
include(":wallet")
include(":server")
include(":appverifier")
include(":server-env")
