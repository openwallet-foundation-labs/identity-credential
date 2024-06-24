plugins {
    id("java-library")
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(17)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(libs.net.sf.scuba.scuba.sc.android)
    implementation(libs.org.jmrtd.jmrtd)
    implementation(libs.kotlinx.io.bytestring)

    testImplementation(libs.junit)
}
