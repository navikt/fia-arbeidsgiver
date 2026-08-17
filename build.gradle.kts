val ktorVersion = "3.5.2"
val kafkaClientVersion = "4.3.1"
val kotlinVersion = "2.4.10"
val logbackVersion = "1.6.1"
val logstashLogbackEncoderVersion = "9.0"
val opentelemetryLogbackMdcVersion = "2.30.0-alpha"
val prometheusVersion = "1.17.0"
val kotestVersion = "6.2.3"
val testcontainersVersion = "2.0.5"
val mockServerVersion = "2.51.1"
val valkeyVersion = "5.5.0"

plugins {
    kotlin("jvm") version "2.4.10"
    kotlin("plugin.serialization") version "2.4.10"
    id("application")
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
    implementation("io.ktor:ktor-client-auth:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-jackson-jvm:$ktorVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.8.0-0.6.x-compat")

    // Kafka
    implementation("at.yawk.lz4:lz4-java:1.11.2")
    implementation("org.apache.kafka:kafka-clients:$kafkaClientVersion") {
        // "Fikser CVE-2025-12183 - lz4-java >1.8.1 har sårbar versjon (transitive dependency fra kafka-clients:4.1.0)"
        exclude("org.lz4", "lz4-java")
    }

    // Valkey client
    implementation("io.valkey:valkey-java:$valkeyVersion")

    // JWT utilities
    implementation("com.nimbusds:nimbus-jose-jwt:10.9.1")

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
    testImplementation("org.testcontainers:testcontainers-kafka:$testcontainersVersion")
    // Mockserver neolight
    testImplementation("software.xdev.mockserver:testcontainers:$mockServerVersion")
    testImplementation("software.xdev.mockserver:client:$mockServerVersion")

    testImplementation("org.wiremock:wiremock-standalone:3.13.2")
    // Mock-oauth2-server
    testImplementation("no.nav.security:mock-oauth2-server:6.0.0")

    constraints {
        implementation("com.fasterxml.jackson.core:jackson-core") {
            version { require("2.22.1") }
            because("versjoner < 2.22.1 har sårbarhet. inkludert i ktor-server-auth:3.4.0")
        }
        implementation("tools.jackson.core:jackson-core") {
            version { require("3.2.1") }
            because("versjoner <= 3.2.0 har sårbarhet. inkludert i logstash-logback-encoder:9.0")
        }
        implementation("io.netty:netty-codec-http2") {
            version {
                require("4.2.16.Final")
            }
            because(
                "versjoner < 4.2.16.Final har sårbarhet.",
            )
        }
    }
}

tasks {
    test {
        dependsOn(installDist)
    }
}
