package no.nav

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import no.nav.kafka.FiaKartleggingKonsument
import no.nav.kafka.FiaStatusKonsument
import no.nav.konfigurasjon.RateLimitKonfig
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
    install(RateLimit) {
        register(RateLimitName("kartlegging")){
            rateLimiter(limit = RateLimitKonfig.generellLimit, refillPeriod = RateLimitKonfig.refillPeriod)
        }
        register(RateLimitName("kartlegging-bli-med")){
            rateLimiter(limit = RateLimitKonfig.bliMedLimit, refillPeriod = RateLimitKonfig.refillPeriod)
        }
    }
    configureStatusPages()
    configureRouting(redisService = RedisService())
}
