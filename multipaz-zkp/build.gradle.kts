import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.ksp)
    id("maven-publish")
}

val projectVersionCode: Int by rootProject.extra
val projectVersionName: String by rootProject.extra

kotlin {
    jvmToolchain(17)

    jvm()

    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }

        publishLibraryVariants("release")
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.kotlinx.io.bytestring)
                implementation(libs.kotlinx.datetime)
                implementation(project(":multipaz"))
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
    }
}

android {
    namespace = "org.multipaz.zkp"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
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

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

group = "org.multipaz"
version = projectVersionName

publishing {
    repositories {
        maven {
            url = uri("${rootProject.rootDir}/repo")
        }
    }
    publications.withType(MavenPublication::class) {
        pom {
            licenses {
                license {
                    name = "Apache 2.0"
                    url = "https://opensource.org/licenses/Apache-2.0"
                }
            }
        }
    }
}

tasks.withType<Test>().configureEach {
    testLogging {
        showStandardStreams = true
    }
    if (name == "jvmTest") {
        // Set the path to your native libraries
        val nativeLibPath = project.file("src/jvmMain/resources/nativeLibs").absolutePath

        // Update the JVM's library path for jvmTest
        jvmArgs = listOf("-Djava.library.path=$nativeLibPath")

        // Ensure native libraries are included in the runtime classpath
        systemProperty("java.library.path", nativeLibPath)
    }
}
