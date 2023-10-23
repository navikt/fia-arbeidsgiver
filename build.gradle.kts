val ktorVersion = "2.3.5"
val kotlinVersion = "1.9.10"
val logbackVersion = "1.4.11"
val prometeusVersion  = "1.11.5"

plugins {
    kotlin("jvm") version "1.9.10"
    kotlin("plugin.serialization") version "1.9.10"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "no.nav"

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-metrics-micrometer-jvm:$ktorVersion")
    implementation("io.micrometer:micrometer-registry-prometheus:$prometeusVersion")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jwt-jvm:$ktorVersion")
    constraints {
        implementation("com.google.guava:guava") {
            version {
                require("32.1.3-jre")
            }
            because("ktor-server-auth-jwt:2.3.5 inkluderer guava 30.x.x som er sårbar for cve-2023-2976")
        }
    }
    implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("net.logstash.logback:logstash-logback-encoder:7.4")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.1")

    // Kafka
    implementation("org.apache.kafka:kafka-clients:3.6.0")

    // Lettuce - Redis client
    implementation("io.lettuce:lettuce-core:6.2.6.RELEASE")

    // altinn-klient
    implementation("no.nav.arbeidsgiver:altinn-rettigheter-proxy-klient:3.1.0")

    // JWT utilities
    implementation("com.nimbusds:nimbus-jose-jwt:9.37")

    // audit log
    implementation("com.papertrailapp:logback-syslog4j:1.0.0")

    val kotestVersion = "5.7.2"
    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
    testImplementation("io.kotest:kotest-assertions-json:$kotestVersion")
    testImplementation("io.ktor:ktor-server-tests-jvm:$ktorVersion")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlinVersion")
    val testcontainersVersion = "1.19.1"
    testImplementation("org.testcontainers:testcontainers:$testcontainersVersion")
    testImplementation("org.testcontainers:kafka:$testcontainersVersion")
    testImplementation("com.github.tomakehurst:wiremock-jre8-standalone:3.0.1")
    // Mock-oauth2-server
    testImplementation("no.nav.security:mock-oauth2-server:2.0.0")
    constraints {
        implementation("net.minidev:json-smart") {
            version {
                require("2.5.0")
            }
            because("From Kotlin version: 1.7.20 -> Earlier versions of json-smart package are vulnerable to Denial of Service (DoS) due to a StackOverflowError when parsing a deeply nested JSON array or object.")
        }
    }
}

tasks {
    shadowJar {
        manifest {
            attributes("Main-Class" to "no.nav.ApplicationKt")
        }
    }
    test {
        dependsOn(shadowJar)
    }
}
