package no.nav

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import no.nav.kafka.FiaStatusKonsument
import no.nav.persistence.RedisService
import no.nav.plugins.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val logger: Logger = LoggerFactory.getLogger("Application::main")

fun main() {
    try {

        val redisService = RedisService()
        FiaStatusKonsument(redisService).run()
        embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module).start(wait = true)

    } catch (e: Exception) {
        logger.error("Failed to start application", e)
    }
}

fun Application.module() {
    configureMonitoring()
    configureSerialization()
    configureSecurity()
    configureRouting(redisService = RedisService())
}
