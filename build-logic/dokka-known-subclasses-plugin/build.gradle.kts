plugins {
    kotlin("jvm") version "2.2.21"
    id("org.jetbrains.dokka") version "2.1.0"
    `maven-publish`
    signing
}

group = "org.multipaz"
version = "1.0.0"

repositories {
    mavenCentral()
}

val dokkaVersion = "2.1.0"

kotlin {
    jvmToolchain(21)
}

dependencies {
    compileOnly("org.jetbrains.dokka:dokka-core:$dokkaVersion")
    implementation("org.jetbrains.dokka:dokka-base:$dokkaVersion")

    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.dokka:dokka-test-api:$dokkaVersion")
    testImplementation("org.jetbrains.dokka:dokka-base-test-utils:$dokkaVersion")
    testImplementation("org.jetbrains.dokka:analysis-kotlin-symbols:$dokkaVersion")
}

tasks.dokkaHtml {
    outputDirectory.set(layout.buildDirectory.dir("dokka"))
}

val javadocJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
    from(tasks.dokkaHtml)
}

java {
    withSourcesJar()
}

publishing {
    publications {
        val knownSubclassesPluginPublication by creating(MavenPublication::class) {
            artifactId = project.name
            from(components["java"])
            artifact(javadocJar)

            pom {
                name.set("Dokka Known Subclasses Plugin")
                description.set("This is a plugin for listing the classes that extends the current class")
                url.set("https://github.com/openwallet-foundation/multipaz/tree/main/build-logic/dokka-known-subclasses-plugin/")

                licenses {
                    license {
                        name.set("Apache-2.0")
                        url.set("https://opensource.org/licenses/Apache-2.0")
                        distribution.set("repo")
                    }
                }

                developers {
                    developer {
                        id.set("VishnuSanal")
                        name.set("Vishnu Sanal T")
                        email.set("vishnusanalt@gmail.com")
                        organization.set("Open Wallet Foundation")
                        organizationUrl.set("https://openwallet.foundation/")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/openwallet-foundation/multipaz.git")
                    developerConnection.set("scm:git:ssh://github.com/openwallet-foundation/multipaz.git")
                    url.set("https://github.com/openwallet-foundation/multipaz")
                }
            }
        }
        signPublicationsIfKeyPresent(knownSubclassesPluginPublication)
    }

    repositories {
        maven("https://oss.sonatype.org/service/local/staging/deploy/maven2/") {
            credentials {
                username = System.getenv("SONATYPE_USER")
                password = System.getenv("SONATYPE_PASSWORD")
            }
        }
    }
}

fun Project.signPublicationsIfKeyPresent(publication: MavenPublication) {
    val signingKeyId: String? = System.getenv("SIGN_KEY_ID")
    val signingKey: String? = System.getenv("SIGN_KEY")
    val signingKeyPassphrase: String? = System.getenv("SIGN_KEY_PASSPHRASE")

    if (!signingKey.isNullOrBlank()) {
        extensions.configure<SigningExtension>("signing") {
            if (signingKeyId?.isNotBlank() == true) {
                useInMemoryPgpKeys(signingKeyId, signingKey, signingKeyPassphrase)
            } else {
                useInMemoryPgpKeys(signingKey, signingKeyPassphrase)
            }
            sign(publication)
        }
    }
}
