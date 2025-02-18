rootProject.name = "IdentityCredential"
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

include(":processor")
include(":processor-annotations")
include(":identity")
include(":identity:SwiftBridge")
include(":identity-flow")
include(":identity-mdoc")
include(":identity-sdjwt")
include(":identity-doctypes")
include(":identity-android")
include(":identity-issuance")
include(":identity-issuance-api")
include(":identity-appsupport")
include(":identity-csa")
include(":identity-android-legacy")
include(":identityctl")
include(":mrtd-reader")
include(":mrtd-reader-android")
include(":multipaz-compose")
include(":jpeg2k")
include(":samples:testapp")
include(":samples:preconsent-mdl")
include(":samples:age-verifier-mdl")
include(":samples:simple-verifier")
include(":wallet")
include(":server")
include(":appverifier")
include(":server-env")
include(":server-openid4vci")
