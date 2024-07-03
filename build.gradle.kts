val ktorVersion = "2.3.11"
val kotlinVersion = "2.0.0"
val logbackVersion = "1.5.6"
val prometheusVersion = "1.13.1"
val iaFellesVersion = "1.2.0-RC"

plugins {
    kotlin("jvm") version "2.0.0"
    kotlin("plugin.serialization") version "2.0.0"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "no.nav"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    val iaFellesVersion = "1.2.0-RC2"
    // Felles definisjoner for IA-domenet
    implementation("com.github.navikt:ia-felles:$iaFellesVersion")

    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-metrics-micrometer-jvm:$ktorVersion")
    implementation("io.micrometer:micrometer-registry-prometheus:$prometheusVersion")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jwt-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-rate-limit:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages-jvm:$ktorVersion")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("net.logstash.logback:logstash-logback-encoder:7.4")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")

    // Kafka
    implementation("org.apache.kafka:kafka-clients:3.7.0")

    // Lettuce - Redis client
    implementation("io.lettuce:lettuce-core:6.3.2.RELEASE")

    // altinn-klient
    implementation("no.nav.arbeidsgiver:altinn-rettigheter-proxy-klient:4.0.0")

    // altinn-rettigheter-proxy bruker codec 1.11 som har en sårbarhet
    implementation("commons-codec:commons-codec:1.17.0")

    // JWT utilities
    implementation("com.nimbusds:nimbus-jose-jwt:9.40")


    // Felles definisjoner for IA-domenet
    implementation("com.github.navikt:ia-felles:$iaFellesVersion")

    // audit log
    implementation("com.papertrailapp:logback-syslog4j:1.0.0")

    val kotestVersion = "5.9.1"
    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
    testImplementation("io.kotest:kotest-assertions-json:$kotestVersion")
    testImplementation("io.ktor:ktor-server-tests-jvm:$ktorVersion")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlinVersion")
    val testcontainersVersion = "1.19.8"
    testImplementation("org.testcontainers:testcontainers:$testcontainersVersion")
    testImplementation("org.testcontainers:kafka:$testcontainersVersion")
    testImplementation("org.wiremock:wiremock-standalone:3.6.0")
    // Mock-oauth2-server
    testImplementation("no.nav.security:mock-oauth2-server:2.1.6")
    constraints {
        implementation("net.minidev:json-smart") {
            version {
                require("2.5.1")
            }
            because("From Kotlin version: 1.7.20 -> Earlier versions of json-smart package are vulnerable to Denial of Service (DoS) due to a StackOverflowError when parsing a deeply nested JSON array or object.")
        }
        implementation("io.netty:netty-codec-http2") {
            version {
                require("4.1.111.Final")
            }
            because("From Ktor version: 2.3.5 -> io.netty:netty-codec-http2 vulnerable to HTTP/2 Rapid Reset Attack")
        }
        testImplementation("org.apache.commons:commons-compress") {
            version {
                require("1.26.2")
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
