val ktorVersion = "3.1.1"
val kafkClientVersion = "3.9.0"
val kotlinVersion = "2.1.10"
val logbackVersion = "1.5.17"
val prometheusVersion = "1.14.4"
val iaFellesVersion = "1.10.2"
val kotestVersion = "5.9.1"
val testcontainersVersion = "1.20.6"
val testMockServerVersion = "5.15.0"
val valkeyVersion = "5.3.0"

plugins {
    kotlin("jvm") version "2.1.10"
    kotlin("plugin.serialization") version "2.1.10"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "no.nav"

repositories {
    mavenCentral()
    maven {
        url = uri("https://jitpack.io")
    }
}

dependencies {
    // Felles definisjoner for IA-domenet
    implementation("com.github.navikt:ia-felles:$iaFellesVersion")

    implementation("io.micrometer:micrometer-registry-prometheus:$prometheusVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-metrics-micrometer-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jwt-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-rate-limit:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-apache:$ktorVersion")
    implementation("io.ktor:ktor-client-auth:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-jackson-jvm:$ktorVersion")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("net.logstash.logback:logstash-logback-encoder:8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2")

    // Kafka
    implementation("org.apache.kafka:kafka-clients:$kafkClientVersion")

    // Valkey client
    implementation("io.valkey:valkey-java:$valkeyVersion")

    // JWT utilities
    implementation("com.nimbusds:nimbus-jose-jwt:10.0.2")

    // audit log
    implementation("com.papertrailapp:logback-syslog4j:1.0.0")

    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
    testImplementation("io.kotest:kotest-assertions-json:$kotestVersion")

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlinVersion")

    testImplementation("org.testcontainers:testcontainers:$testcontainersVersion")
    testImplementation("org.testcontainers:kafka:$testcontainersVersion")
    testImplementation("org.testcontainers:mockserver:$testcontainersVersion")
    testImplementation("org.mock-server:mockserver-client-java:$testMockServerVersion")

    testImplementation("org.wiremock:wiremock-standalone:3.12.1")
    // Mock-oauth2-server
    testImplementation("no.nav.security:mock-oauth2-server:2.1.10")
    constraints {
        testImplementation("com.google.guava:guava") {
            version {
                require("33.4.0-jre")
            }
            because("Mockserver har sårbar guava versjon")
        }
        testImplementation("org.bouncycastle:bcprov-jdk18on") {
            version {
                require("1.80")
            }
            because("bcprov-jdk18on in Mockserver har sårbar versjon")
        }
        testImplementation("org.bouncycastle:bcpkix-jdk18on") {
            version {
                require("1.80")
            }
            because("bcpkix-jdk18on in Mockserver har sårbar versjon")
        }
        testImplementation("org.xmlunit:xmlunit-core") {
            version {
                require("2.10.0")
            }
            because("xmlunit-core in Mockserver har sårbar versjon")
        }
        implementation("net.minidev:json-smart") {
            version {
                require("2.5.2")
            }
            because(
                "versjoner < 2.5.2 har diverse sårbarheter",
            )
        }
        implementation("io.netty:netty-codec-http2") {
            version {
                require("4.1.119.Final")
            }
            because("From Ktor version: 2.3.5 -> io.netty:netty-codec-http2 vulnerable to HTTP/2 Rapid Reset Attack")
        }
        testImplementation("org.apache.commons:commons-compress") {
            version {
                require("1.27.1")
            }
            because("testcontainers har sårbar versjon")
        }
        testImplementation("commons-io:commons-io") {
            version {
                require("2.18.0")
            }
            because("testcontainers har sårbar versjon")
        }
        testImplementation("com.jayway.jsonpath:json-path") {
            version {
                require("2.9.0")
            }
            because(
                """
                json-path v2.8.0 was discovered to contain a stack overflow via the Criteria.parse() method.
                introdusert gjennom io.kotest:kotest-assertions-json:5.8.0 (Mockserver)
                """.trimIndent(),
            )
        }
    }
}

tasks {
    shadowJar {
        manifest {
            attributes("Main-Class" to "no.nav.fia.arbeidsgiver.ApplicationKt")
        }
    }
    test {
        dependsOn(shadowJar)
    }
}
