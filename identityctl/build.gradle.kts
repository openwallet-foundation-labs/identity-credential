plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
    alias(libs.plugins.buildconfig)
}

val projectVersionCode: Int by rootProject.extra
val projectVersionName: String by rootProject.extra

buildConfig {
    packageName("com.android.identity.identityctl")
    buildConfigField("VERSION", projectVersionName)
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
    implementation(project(":identity-mdoc"))

    implementation(libs.kotlinx.datetime)
    implementation(libs.bouncy.castle.bcprov)
    implementation(libs.bouncy.castle.bcpkix)
}

tasks.register("runIdentityCtl", JavaExec::class) {
    group = "Execution"
    description = "Invoke identityctl"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "com.android.identity.identityctl.IdentityCtl"
}
