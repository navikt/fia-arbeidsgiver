val ktorVersion = "3.3.0"
val kafkClientVersion = "4.1.0"
val kotlinVersion = "2.2.20"
val logbackVersion = "1.5.18"
val logstashLogbackEncoderVersion = "8.1"
val opentelemetryLogbackMdcVersion = "2.16.0-alpha"
val prometheusVersion = "1.15.4"
val kotestVersion = "6.0.3"
val testcontainersVersion = "1.21.3"
val mockServerVersion = "1.1.3"
val valkeyVersion = "5.4.0"

plugins {
    kotlin("jvm") version "2.2.20"
    kotlin("plugin.serialization") version "2.2.20"
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
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2")

    // Kafka
    implementation("org.apache.kafka:kafka-clients:$kafkClientVersion")

    // Valkey client
    implementation("io.valkey:valkey-java:$valkeyVersion")

    // JWT utilities
    implementation("com.nimbusds:nimbus-jose-jwt:10.5")

    // audit log
    implementation("com.papertrailapp:logback-syslog4j:1.0.0")
    // logger

    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashLogbackEncoderVersion")
    implementation("io.opentelemetry.instrumentation:opentelemetry-logback-mdc-1.0:$opentelemetryLogbackMdcVersion")

    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
    testImplementation("io.kotest:kotest-assertions-json:$kotestVersion")

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlinVersion")

    testImplementation("org.testcontainers:testcontainers:$testcontainersVersion")
    testImplementation("org.testcontainers:kafka:$testcontainersVersion")
    // Mockserver neolight
    testImplementation("software.xdev.mockserver:testcontainers:$mockServerVersion")
    testImplementation("software.xdev.mockserver:client:$mockServerVersion")

    testImplementation("org.wiremock:wiremock-standalone:3.13.1")
    // Mock-oauth2-server
    testImplementation("no.nav.security:mock-oauth2-server:2.3.0")
    constraints {
        implementation("commons-codec:commons-codec") {
            version {
                require("1.19.0")
            }
            because(
                "ktor-client-apache:3.2.3 har en sårbar versjon av commons-codec",
            )
        }
        testImplementation("org.apache.commons:commons-compress") {
            version {
                require("1.28.0")
            }
            because("testcontainers har sårbar versjon")
        }
        testImplementation("commons-io:commons-io") {
            version {
                require("2.20.0")
            }
            because("testcontainers har sårbar versjon")
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
