import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.get
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.konan.target.HostManager

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
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach {
        val platform = when (it.name) {
            "iosX64" -> "iphonesimulator"
            "iosArm64" -> "iphoneos"
            "iosSimulatorArm64" -> "iphonesimulator"
            else -> error("Unsupported target ${it.name}")
        }
        if (HostManager.hostIsMac) {
            it.compilations.getByName("main") {
                val SwiftBridge by cinterops.creating {
                    definitionFile.set(project.file("nativeInterop/cinterop/SwiftBridge-$platform.def"))
                    includeDirs.headerFilterOnly("$rootDir/identity/SwiftBridge/build/Release-$platform/include")

                    val interopTask = tasks[interopProcessingTaskName]
                    interopTask.dependsOn(":identity:SwiftBridge:build${platform.capitalize()}")
                }

                it.binaries.all {
                    // Linker options required to link to the library.
                    linkerOpts(
                        "-L/Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/lib/swift/${platform}/",
                        "-L$rootDir/identity/SwiftBridge/build/Release-${platform}/",
                        "-lSwiftBridge"
                    )
                }
            }
        }
    }

    // we want some extra dependsOn calls to create
    // javaSharedMain to share between JVM and Android,
    // but otherwise want to follow default hierarchy.
    applyDefaultHierarchyTemplate()

    sourceSets {
        val commonMain by getting {
            kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")
            dependencies {
                implementation(projects.processorAnnotations)
                implementation(libs.kotlinx.io.bytestring)
                implementation(libs.kotlinx.io.core)
                implementation(libs.kotlinx.datetime)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.cio)

                // TODO: remove when JsonWebEncryption is implemented fully in Kotlin
                implementation(libs.nimbus.oauth2.oidc.sdk)
            }
        }

        val commonTest by getting {
            kotlin.srcDir("build/generated/ksp/metadata/commonTest/kotlin")
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        val javaSharedMain by creating {
            dependsOn(commonMain)
            dependencies {
                implementation(libs.bouncy.castle.bcprov)
                implementation(libs.bouncy.castle.bcpkix)
                implementation(libs.tink)
            }
        }

        val jvmMain by getting {
            dependsOn(javaSharedMain)
            dependencies {
                implementation(libs.bouncy.castle.bcprov)
                implementation(libs.bouncy.castle.bcpkix)
                implementation(libs.tink)
            }
        }

        val androidMain by getting {
            dependsOn(javaSharedMain)
            dependencies {
                implementation(libs.bouncy.castle.bcprov)
                implementation(libs.bouncy.castle.bcpkix)
                implementation(libs.tink)
                implementation(libs.androidx.biometrics)
                implementation(libs.androidx.lifecycle.viewmodel)
            }
        }

        val iosMain by getting {
            dependencies {
                // This dependency is needed for SqliteStorage implementation.
                // KMP-compatible version is still alpha and it is not compatible with
                // other androidx packages, particularly androidx.work that we use in wallet.
                // TODO: once compatibility issues are resolved, SqliteStorage and this
                // dependency can be moved into commonMain.
                implementation(libs.androidx.sqlite)
                implementation(libs.androidx.sqlite.framework)
                implementation(libs.ktor.client.darwin)
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(libs.hsqldb)
                implementation(libs.mysql)
                implementation(libs.postgresql)
                implementation(libs.nimbus.oauth2.oidc.sdk)
                implementation(libs.kotlinx.serialization.json)
            }
        }

        val androidInstrumentedTest by getting {
            dependsOn(commonTest)
            dependencies {
                implementation(libs.androidx.sqlite)
                implementation(libs.androidx.sqlite.framework)
                implementation(libs.androidx.sqlite.bundled)
                implementation(libs.androidx.test.junit)
                implementation(libs.androidx.espresso.core)
                implementation(libs.kotlinx.coroutines.android)
                implementation(project(":identity-csa"))
            }
        }

        val iosTest by getting {
            dependencies {
                implementation(libs.androidx.sqlite)
                implementation(libs.androidx.sqlite.framework)
                implementation(libs.androidx.sqlite.bundled)
            }
        }
    }
}

dependencies {
    add("kspCommonMainMetadata", project(":processor"))
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().all {
    if (name != "kspCommonMainKotlinMetadata") {
        dependsOn("kspCommonMainKotlinMetadata")
    }
}

tasks["compileKotlinIosX64"].dependsOn("kspCommonMainKotlinMetadata")
tasks["compileKotlinIosArm64"].dependsOn("kspCommonMainKotlinMetadata")
tasks["compileKotlinIosSimulatorArm64"].dependsOn("kspCommonMainKotlinMetadata")

tasks["iosX64SourcesJar"].dependsOn("kspCommonMainKotlinMetadata")
tasks["iosArm64SourcesJar"].dependsOn("kspCommonMainKotlinMetadata")
tasks["iosSimulatorArm64SourcesJar"].dependsOn("kspCommonMainKotlinMetadata")
tasks["jvmSourcesJar"].dependsOn("kspCommonMainKotlinMetadata")
tasks["sourcesJar"].dependsOn("kspCommonMainKotlinMetadata")

android {
    namespace = "com.android.identity"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    dependencies {
    }

    packaging {
        resources {
            excludes += listOf("/META-INF/{AL2.0,LGPL2.1}")
            excludes += listOf("/META-INF/versions/9/OSGI-INF/MANIFEST.MF")
        }
    }

    dependencies {
    }
}


group = "com.android.identity"
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
