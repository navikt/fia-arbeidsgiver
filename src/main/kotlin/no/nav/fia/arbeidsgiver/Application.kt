package no.nav.fia.arbeidsgiver

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import no.nav.fia.arbeidsgiver.konfigurasjon.plugins.configureRouting
import no.nav.fia.arbeidsgiver.konfigurasjon.plugins.configureSerialization
import no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.SpørreundersøkelseKonsument
import no.nav.fia.arbeidsgiver.samarbeidsstatus.kafka.FiaStatusKonsument
import no.nav.fia.arbeidsgiver.redis.RedisService
import no.nav.fia.arbeidsgiver.konfigurasjon.plugins.configureMonitoring
import no.nav.fia.arbeidsgiver.konfigurasjon.plugins.configureSecurity
import no.nav.fia.arbeidsgiver.konfigurasjon.plugins.configureStatusPages
import no.nav.fia.arbeidsgiver.samarbeidsstatus.domene.SamarbeidsstatusService
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.SpørreundersøkelseService
import no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.SpørreundersøkelseAntallSvarKonsument

fun main() {
    val redisService = RedisService()
    FiaStatusKonsument(SamarbeidsstatusService(redisService)).run()
    SpørreundersøkelseKonsument(SpørreundersøkelseService(redisService)).run()
    SpørreundersøkelseAntallSvarKonsument(SpørreundersøkelseService(redisService)).run()
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::fiaArbeidsgiver).start(wait = true)
}

fun Application.fiaArbeidsgiver() {
    configureMonitoring()
    configureSerialization()
    configureSecurity()
    configureStatusPages()
    configureRouting(redisService = RedisService())
}
