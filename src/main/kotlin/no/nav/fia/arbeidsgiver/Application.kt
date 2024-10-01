package no.nav.fia.arbeidsgiver

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import no.nav.fia.arbeidsgiver.konfigurasjon.ApplikasjonsHelse
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
import no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.SpørreundersøkelseOppdateringKonsument

fun main() {
    val applikasjonsHelse = ApplikasjonsHelse()
    val redisService = RedisService()

    FiaStatusKonsument(SamarbeidsstatusService(redisService), applikasjonsHelse).run()
    SpørreundersøkelseKonsument(SpørreundersøkelseService(redisService), applikasjonsHelse).run()
    SpørreundersøkelseOppdateringKonsument(SpørreundersøkelseService(redisService), applikasjonsHelse).run()

    val applikasjonsServer = embeddedServer(
        factory = Netty,
        port = 8080,
        host = "0.0.0.0",
        module = { fiaArbeidsgiver(redisService, applikasjonsHelse) }
    )
    applikasjonsHelse.ready = true

    Runtime.getRuntime().addShutdownHook(
        Thread {
            applikasjonsHelse.ready = false
            applikasjonsHelse.alive = false
            applikasjonsServer.stop(1000, 5000)
        }
    )

    applikasjonsServer.start(wait = true)
}

fun Application.fiaArbeidsgiver(
    redisService: RedisService,
    applikasjonsHelse: ApplikasjonsHelse,
) {
    configureMonitoring()
    configureSerialization()
    configureSecurity()
    configureStatusPages()
    configureRouting(redisService = redisService, applikasjonsHelse = applikasjonsHelse)
}


