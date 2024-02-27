val ktorVersion = "2.3.8"
val kotlinVersion = "1.9.22"
val logbackVersion = "1.4.14"
val prometeusVersion  = "1.12.2"

plugins {
    kotlin("jvm") version "1.9.22"
    kotlin("plugin.serialization") version "1.9.22"
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
    implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-rate-limit:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages-jvm:$ktorVersion")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("net.logstash.logback:logstash-logback-encoder:7.4")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")

    // Kafka
    implementation("org.apache.kafka:kafka-clients:3.6.1")

    // Lettuce - Redis client
    implementation("io.lettuce:lettuce-core:6.3.1.RELEASE")

    // altinn-klient
    implementation("no.nav.arbeidsgiver:altinn-rettigheter-proxy-klient:3.1.0")

    // altinn-rettigheter-proxy bruker codec 1.11 som har en sårbarhet
    implementation("commons-codec:commons-codec:1.16.0")

    // JWT utilities
    implementation("com.nimbusds:nimbus-jose-jwt:9.37.3")

    // audit log
    implementation("com.papertrailapp:logback-syslog4j:1.0.0")

    val kotestVersion = "5.8.0"
    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
    testImplementation("io.kotest:kotest-assertions-json:$kotestVersion")
    testImplementation("io.ktor:ktor-server-tests-jvm:$ktorVersion")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlinVersion")
    val testcontainersVersion = "1.19.4"
    testImplementation("org.testcontainers:testcontainers:$testcontainersVersion")
    testImplementation("org.testcontainers:kafka:$testcontainersVersion")
    testImplementation("org.wiremock:wiremock-standalone:3.3.1")
    // Mock-oauth2-server
    testImplementation("no.nav.security:mock-oauth2-server:2.1.1")
    constraints {
        implementation("net.minidev:json-smart") {
            version {
                require("2.5.0")
            }
            because("From Kotlin version: 1.7.20 -> Earlier versions of json-smart package are vulnerable to Denial of Service (DoS) due to a StackOverflowError when parsing a deeply nested JSON array or object.")
        }
        implementation("io.netty:netty-codec-http2") {
            version {
                require("4.1.106.Final")
            }
            because("From Ktor version: 2.3.5 -> io.netty:netty-codec-http2 vulnerable to HTTP/2 Rapid Reset Attack")
        }
        testImplementation("com.jayway.jsonpath:json-path") {
            version {
                require("2.9.0")
            }
            because(
                """
                json-path v2.8.0 was discovered to contain a stack overflow via the Criteria.parse() method.
                introdusert gjennom io.kotest:kotest-assertions-json:5.8.0
                """.trimIndent()
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
