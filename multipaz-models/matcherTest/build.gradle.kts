plugins {
    alias(libs.plugins.androidLibrary)
}


android {
    namespace = "org.multipaz.models.presentment"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = 26
    }

    externalNativeBuild {
        cmake {
            path("CMakeLists.txt")
        }
    }
}

