import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.2.21"
    `java-gradle-plugin`
}

kotlin {
    jvmToolchain(21)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

gradlePlugin {
    plugins {
        create("lokalizeConvention") {
            id = "org.multipaz.lokalize.convention"
            implementationClass = "org.multipaz.lokalize.convention.LokalizeConventionPlugin"
        }
    }
}

dependencies {
    compileOnly(gradleApi())
    compileOnly(libs.gradlePlugin.kotlin)
    compileOnly(libs.android.tools.gradle.plugin)

    implementation(project(":lokalize"))
}
