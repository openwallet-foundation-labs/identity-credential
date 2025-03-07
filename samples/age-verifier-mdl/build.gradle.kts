plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.jetbrainsCompose)
    id("kotlin-android")
}

val projectVersionCode: Int by rootProject.extra
val projectVersionName: String by rootProject.extra

kotlin {
    jvmToolchain(17)
}

android {
    namespace = "org.multipaz.age_verifier_mdl"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "org.multipaz.age_verifier_mdl"
        minSdk = 28
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = projectVersionCode
        versionName = projectVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += listOf("/META-INF/{AL2.0,LGPL2.1}")
            excludes += listOf("/META-INF/versions/9/OSGI-INF/MANIFEST.MF")
        }
    }
}

dependencies {
    implementation(project(":multipaz"))
    implementation(project(":multipaz-android-legacy"))

    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.io.core)

    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.material)
    implementation(compose.ui)
    implementation(compose.components.resources)
    implementation(compose.components.uiToolingPreview)
    implementation(compose.material)

    debugImplementation(compose.uiTooling)
    implementation(compose.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.biometrics)
    implementation(compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.androidx.navigation.runtime)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.appcompat)

    implementation(libs.bouncy.castle.bcprov)

    testImplementation(libs.kotlin.test)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}