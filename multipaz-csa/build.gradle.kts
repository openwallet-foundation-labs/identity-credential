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
    implementation(project(":multipaz"))
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.io.bytestring)

    implementation(project(":multipaz-cbor-rpc-annotations"))
    ksp(project(":multipaz-cbor-rpc"))

    testImplementation(libs.kotlin.test)
    testImplementation(libs.bouncy.castle.bcprov)
    testImplementation(libs.bouncy.castle.bcpkix)
}
