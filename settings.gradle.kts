rootProject.name = "IdentityCredential"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

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

include(":processor")
include(":processor-annotations")
include(":identity")
include(":identity:SwiftBridge")
include(":identity-flow")
include(":identity-mdoc")
include(":identity-sdjwt")
include(":identity-doctypes")
include(":identity-android")
include(":identity-android-legacy")
include(":identity-issuance")
include(":identity-appsupport")
include(":identity-csa")
include(":identity-android-csa")
include(":identityctl")
include(":mrtd-reader")
include(":mrtd-reader-android")
include(":jpeg2k")
include(":samples:testapp")
include(":samples:preconsent-mdl")
include(":samples:age-verifier-mdl")
include(":samples:simple-verifier")
include(":samples:connectivity-testing")
include(":wallet")
include(":server")
include(":appverifier")
include(":appholder")
include(":server-env")
include(":server-openid4vci")
