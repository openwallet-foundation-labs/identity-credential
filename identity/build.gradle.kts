
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.ksp)
    id("io.github.ttypic.swiftklib") version "0.5.1"
}

kotlin {
    jvmToolchain(17)

    jvm()

    if (!project.hasProperty("disableIosSupport")) {
        listOf(
            iosX64(),
            iosArm64(),
            iosSimulatorArm64()
        ).forEach {
            it.compilations {
                val main by getting {
                    cinterops {
                        create("SwiftCrypto")
                    }
                }
            }
            it.binaries.framework {
                baseName = "identity"
                isStatic = true
            }
        }
    }

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
            }
            languageSettings {
                languageVersion = "1.9"
                apiVersion = "1.9"
            }
        }

        val commonTest by getting {
            kotlin.srcDir("build/generated/ksp/metadata/commonTest/kotlin")
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutine.test)
            }
            languageSettings {
                languageVersion = "1.9"
                apiVersion = "1.9"
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation(libs.bouncy.castle.bcprov)
                implementation(libs.bouncy.castle.bcpkix)
                implementation(libs.tink)
            }
            languageSettings {
                languageVersion = "1.9"
                apiVersion = "1.9"
            }
        }
    }
}

dependencies {
    if (project.hasProperty("disableIosSupport")) {
        add("kspJvm", project(":processor"))
    }
    add("kspCommonMainMetadata", project(":processor"))
}

if (!project.hasProperty("disableIosSupport")) {
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().all {
        if (name != "kspCommonMainKotlinMetadata") {
            dependsOn("kspCommonMainKotlinMetadata")
        }
    }
}

if (!project.hasProperty("disableIosSupport")) {
    tasks["compileKotlinIosX64"].dependsOn("kspCommonMainKotlinMetadata")
    tasks["compileKotlinIosArm64"].dependsOn("kspCommonMainKotlinMetadata")
    tasks["compileKotlinIosSimulatorArm64"].dependsOn("kspCommonMainKotlinMetadata")
}

swiftklib {
    if (!project.hasProperty("disableIosSupport")) {
        create("SwiftCrypto") {
            path = file("native/SwiftCrypto")
            packageName("com.android.identity.swiftcrypto")
        }
    }
}
