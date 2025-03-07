plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
    alias(libs.plugins.buildconfig)
}

val projectVersionCode: Int by rootProject.extra
val projectVersionName: String by rootProject.extra

buildConfig {
    packageName("org.multipaz.multipazctl")
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
    implementation(project(":multipaz"))

    implementation(libs.kotlinx.datetime)
    implementation(libs.bouncy.castle.bcprov)
    implementation(libs.bouncy.castle.bcpkix)
}

tasks.register("runMultipazCtl", JavaExec::class) {
    group = "Execution"
    description = "Invoke multipazctl"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "org.multipaz.multipazctl.MultipazCtl"
}
