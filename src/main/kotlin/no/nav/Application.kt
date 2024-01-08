package no.nav

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import no.nav.kafka.FiaKartleggingKonsument
import no.nav.kafka.FiaStatusKonsument
import no.nav.persistence.RedisService
import no.nav.plugins.*

fun main() {
    val redisService = RedisService()
    FiaStatusKonsument(redisService).run()
    FiaKartleggingKonsument(redisService).run()
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module).start(wait = true)
}

fun Application.module() {
    configureMonitoring()
    configureSerialization()
    configureSecurity()
    configureRouting(redisService = RedisService())
}
