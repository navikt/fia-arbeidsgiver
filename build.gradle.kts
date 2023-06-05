val ktor_version = "2.3.1"
val kotlin_version = "1.8.21"
val logback_version = "1.4.7"
val prometeus_version  = "1.11.0"

plugins {
    kotlin("jvm") version "1.8.21"
    id("io.ktor.plugin") version "2.3.1"
    id("org.jetbrains.kotlin.plugin.serialization") version "1.8.21"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "no.nav"
version = "0.0.1"
application {
    mainClass.set("no.nav.ApplicationKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.ktor:ktor-server-core-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-metrics-micrometer-jvm:$ktor_version")
    implementation("io.micrometer:micrometer-registry-prometheus:$prometeus_version")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktor_version")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-auth-jvm:$ktor_version")
    implementation("io.ktor:ktor-client-core:$ktor_version")
    implementation("io.ktor:ktor-client-cio:$ktor_version")
    implementation("io.ktor:ktor-server-auth-jwt-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-netty-jvm:$ktor_version")
    implementation("ch.qos.logback:logback-classic:$logback_version")
    implementation("net.logstash.logback:logstash-logback-encoder:7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.0")

    // Kafka
    implementation("org.apache.kafka:kafka-clients:3.4.0")

    // Lettuce - Redis client
    implementation("io.lettuce:lettuce-core:6.2.4.RELEASE")

    // altinn-klient
    implementation("no.nav.arbeidsgiver:altinn-rettigheter-proxy-klient:3.1.0")

    // JWT utilities
    implementation("com.nimbusds:nimbus-jose-jwt:9.31")

    // audit log
    implementation("com.papertrailapp:logback-syslog4j:1.0.0")

    val kotestVersion = "5.6.2"
    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
    testImplementation("io.kotest:kotest-assertions-json:$kotestVersion")
    testImplementation("io.ktor:ktor-server-tests-jvm:$ktor_version")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
    val testcontainersVersion = "1.18.3"
    testImplementation("org.testcontainers:testcontainers:$testcontainersVersion")
    testImplementation("org.testcontainers:kafka:$testcontainersVersion")
    testImplementation("com.github.tomakehurst:wiremock-jre8-standalone:2.35.0")
    // Mock-oauth2-server
    testImplementation("no.nav.security:mock-oauth2-server:0.5.8")
    constraints {
        implementation("net.minidev:json-smart:2.4.11") {
            because("From Kotlin version: 1.7.20 -> Earlier versions of json-smart package are vulnerable to Denial of Service (DoS) due to a StackOverflowError when parsing a deeply nested JSON array or object.")
        }
    }
}

tasks {
    shadowJar {
        manifest {
            attributes(Pair("Main-Class", "no.nav.lydia.AppKt"))
        }
    }
    withType<Test> {
        dependsOn("shadowJar")
    }
}
