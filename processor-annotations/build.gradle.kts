import org.jetbrains.kotlin.konan.target.HostManager

plugins {
    kotlin("multiplatform")
}

kotlin {
    jvmToolchain(17)

    jvm()

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach {
        it.binaries.framework {
            baseName = "identity"
            isStatic = true
        }
    }

    sourceSets {
        commonMain {
            // configure the commonMain source set
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

// Workaround for gradle error whey to find :identity-appsupport:testClasses.
tasks.register("testClasses") {
    dependsOn("jvmTestClasses")
}
