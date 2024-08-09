plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
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
    implementation(project(":identity"))
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.serialization.json)

    implementation(project(":processor-annotations"))
    ksp(project(":processor"))

    testImplementation(libs.kotlin.test)
    testImplementation(libs.bouncy.castle.bcprov)
    testImplementation(libs.bouncy.castle.bcpkix)
}
