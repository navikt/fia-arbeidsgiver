package no.nav.fia.arbeidsgiver

import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import no.nav.fia.arbeidsgiver.konfigurasjon.ApplikasjonsHelse
import no.nav.fia.arbeidsgiver.konfigurasjon.jedisPool
import no.nav.fia.arbeidsgiver.konfigurasjon.plugins.configureMonitoring
import no.nav.fia.arbeidsgiver.konfigurasjon.plugins.configureRouting
import no.nav.fia.arbeidsgiver.konfigurasjon.plugins.configureSecurity
import no.nav.fia.arbeidsgiver.konfigurasjon.plugins.configureSerialization
import no.nav.fia.arbeidsgiver.konfigurasjon.plugins.configureStatusPages
import no.nav.fia.arbeidsgiver.samarbeidsstatus.domene.SamarbeidsstatusService
import no.nav.fia.arbeidsgiver.samarbeidsstatus.kafka.FiaStatusKonsument
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.SpørreundersøkelseService
import no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.SpørreundersøkelseKonsument
import no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.SpørreundersøkelseOppdateringKonsument
import no.nav.fia.arbeidsgiver.valkey.ValkeyService

fun main() {
    val applikasjonsHelse = ApplikasjonsHelse()

    val jedisPool = jedisPool()
    val valkeyService = ValkeyService(jedisPool)

    FiaStatusKonsument(SamarbeidsstatusService(valkeyService), applikasjonsHelse).run()
    SpørreundersøkelseKonsument(SpørreundersøkelseService(valkeyService), applikasjonsHelse).run()
    SpørreundersøkelseOppdateringKonsument(SpørreundersøkelseService(valkeyService), applikasjonsHelse).run()

    val applikasjonsServer = embeddedServer(
        factory = Netty,
        port = 8080,
        host = "0.0.0.0",
        module = { fiaArbeidsgiver(valkeyService, applikasjonsHelse) },
    )
    applikasjonsHelse.ready = true

    Runtime.getRuntime().addShutdownHook(
        Thread {
            applikasjonsHelse.ready = false
            applikasjonsHelse.alive = false
            jedisPool.close()
            applikasjonsServer.stop(1000, 5000)
        },
    )

    applikasjonsServer.start(wait = true)
}

fun Application.fiaArbeidsgiver(
    valkeyService: ValkeyService,
    applikasjonsHelse: ApplikasjonsHelse,
) {
    configureMonitoring()
    configureSerialization()
    configureSecurity()
    configureStatusPages()
    configureRouting(valkeyService = valkeyService, applikasjonsHelse = applikasjonsHelse)
}
