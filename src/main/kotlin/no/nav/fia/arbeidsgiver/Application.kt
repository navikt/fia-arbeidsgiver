package no.nav.fia.arbeidsgiver

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import no.nav.fia.arbeidsgiver.plugins.configureRouting
import no.nav.fia.arbeidsgiver.plugins.configureSerialization
import no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.SpørreundersøkelseKonsument
import no.nav.fia.arbeidsgiver.samarbeidsstatus.kafka.FiaStatusKonsument
import no.nav.fia.arbeidsgiver.redis.RedisService
import no.nav.fia.arbeidsgiver.plugins.configureMonitoring
import no.nav.fia.arbeidsgiver.plugins.configureSecurity
import no.nav.fia.arbeidsgiver.plugins.configureStatusPages
import no.nav.fia.arbeidsgiver.samarbeidsstatus.domene.SamarbeidsstatusService
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.SpørreundersøkelseService

fun main() {
    val redisService = RedisService()
    FiaStatusKonsument(SamarbeidsstatusService(redisService)).run()
    SpørreundersøkelseKonsument(SpørreundersøkelseService(redisService)).run()
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module).start(wait = true)
}

fun Application.module() {
    configureMonitoring()
    configureSerialization()
    configureSecurity()
    configureStatusPages()
    configureRouting(redisService = RedisService())
}
