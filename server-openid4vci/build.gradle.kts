plugins {
    alias(libs.plugins.gretty)
    alias(libs.plugins.kotlinSerialization)
    id("war")
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
    ksp(project(":processor"))
    implementation(project(":identity"))
    implementation(project(":identity-flow"))
    implementation(project(":processor-annotations"))
    implementation(project(":identity-issuance"))
    implementation(project(":identity-csa"))
    implementation(project(":identity-mdoc"))
    implementation(project(":identity-sdjwt"))
    implementation(project(":identity-doctypes"))
    implementation(project(":server-env"))

    implementation(libs.javax.servlet.api)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.io.bytestring)
    implementation(libs.bouncy.castle.bcprov)
    implementation(libs.nimbus.oauth2.oidc.sdk)

    testImplementation(libs.junit)
}

gretty {}
