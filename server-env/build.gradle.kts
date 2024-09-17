plugins {
    alias(libs.plugins.gretty)
    alias(libs.plugins.kotlinSerialization)
    id("java-library")
    alias(libs.plugins.jetbrains.kotlin.jvm)
    alias(libs.plugins.ksp)
}

kotlin {
    jvmToolchain(17)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    ksp(project(":processor"))
    implementation(project(":processor-annotations"))
    implementation(project(":identity"))
    implementation(project(":identity-flow"))

    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.io.bytestring)
    implementation(libs.bouncy.castle.bcprov)
    implementation(libs.hsqldb)
    implementation(libs.mysql)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.java)

    testImplementation(libs.junit)
}