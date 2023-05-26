package no.nav

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import no.nav.plugins.*
import org.slf4j.LoggerFactory

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}
val logger = LoggerFactory.getLogger("mainLog")
fun Application.module() {
    logger.info("MILJØ issuer: ${Miljø.tokenxIssuer}, clientId: ${Miljø.tokenxClientId}")
    configureMonitoring()
    configureSerialization()
    configureSecurity()
    configureRouting()
}
