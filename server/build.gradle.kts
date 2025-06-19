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

    compilerOptions {
        allWarningsAsErrors = true
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    ksp(project(":multipaz-cbor-rpc"))
    implementation(project(":multipaz"))
    implementation(project(":multipaz-provisioning-api"))
    implementation(project(":multipaz-provisioning"))
    implementation(project(":multipaz-csa"))
    implementation(project(":multipaz-doctypes"))
    implementation(project(":server-env"))

    implementation(libs.javax.servlet.api)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.io.bytestring)
    implementation(libs.hsqldb)
    implementation(libs.zxing.core)
    implementation(libs.mysql)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.java)
    implementation(libs.nimbus.oauth2.oidc.sdk)
    implementation(libs.bouncy.castle.bcprov)
    implementation(libs.bouncy.castle.bcpkix)

    testImplementation(libs.junit)
}

gretty {}
