plugins {
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlinMultiplatform)
}

val projectVersionCode: Int by rootProject.extra
val projectVersionName: String by rootProject.extra

kotlin {
    jvmToolchain(17)

    jvm()

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach {
        it.binaries.framework {
            baseName = "multipaz-provisioning-api"
            isStatic = true
        }
    }

    sourceSets {
        val commonMain by getting {
            kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")
            dependencies {
                implementation(projects.multipazCborRpcAnnotations)
                implementation(projects.multipaz)
                implementation(libs.kotlinx.io.bytestring)
                implementation(libs.kotlinx.io.core)
                implementation(libs.kotlinx.datetime)
                implementation(libs.kotlinx.coroutines.core)
            }
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

group = "org.multipaz"
version = projectVersionName

dependencies {
    add("kspCommonMainMetadata", project(":multipaz-cbor-rpc"))
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
