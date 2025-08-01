plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.ktor)
}

project.setProperty("mainClassName", "org.multipaz.verifier.server.Main")

kotlin {
    jvmToolchain(17)

    compilerOptions {
        allWarningsAsErrors = true
        optIn.add("kotlin.time.ExperimentalTime")
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    ksp(project(":multipaz-cbor-rpc"))
    implementation(project(":multipaz"))
    implementation(project(":multipaz-doctypes"))
    implementation(project(":multipaz-models"))
    implementation(project(":multipaz-longfellow"))

    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.io.bytestring)
    implementation(libs.zxing.core)
    implementation(libs.hsqldb)
    implementation(libs.mysql)
    implementation(libs.postgresql)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.java)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.logging)
    implementation(libs.ktor.server.double.receive)
    implementation(libs.logback.classic)
    implementation(libs.nimbus.oauth2.oidc.sdk)

    testImplementation(libs.junit)
}

ktor {
}