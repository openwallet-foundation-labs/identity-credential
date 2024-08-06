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
    ksp(project(":processor"))
    implementation(project(":processor-annotations"))
    implementation(project(":identity"))
    implementation(project(":identity-doctypes"))
    implementation(project(":identity-mdoc"))
    implementation(project(":identity-sdjwt"))
    implementation(project(":identity-flow"))
    implementation(project(":mrtd-reader"))
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.io.core)
    implementation(libs.kotlinx.io.bytestring)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.net.sf.scuba.scuba.sc.android)
    implementation(libs.org.jmrtd.jmrtd)
    implementation(libs.bouncy.castle.bcprov)
    implementation(libs.bouncy.castle.bcpkix)
    implementation(libs.ktor.client.core)

    testImplementation(libs.junit)
}
