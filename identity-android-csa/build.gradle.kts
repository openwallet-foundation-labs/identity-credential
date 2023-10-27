plugins {
    alias(libs.plugins.androidLibrary)
    id("kotlin-android")
}

kotlin {
    jvmToolchain(17)
}

android {
    namespace = "com.android.identity.csa"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += listOf("/META-INF/{AL2.0,LGPL2.1}")
            excludes += listOf("/META-INF/versions/9/OSGI-INF/MANIFEST.MF")
        }
    }
}

dependencies {
    implementation(project(":identity"))
    implementation(project(":identity-android"))
    implementation(project(":identity-csa"))
    implementation(libs.androidx.biometrics)
    implementation(libs.bouncy.castle.bcprov)
    implementation(libs.bouncy.castle.bcpkix)
    implementation(libs.volley)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.io.bytestring)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.ktor.client.android)

    testImplementation(libs.junit)
    androidTestImplementation(libs.kotlinx.coroutine.test)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
