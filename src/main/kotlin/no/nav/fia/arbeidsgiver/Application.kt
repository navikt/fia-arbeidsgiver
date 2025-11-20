package no.nav.fia.arbeidsgiver

import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import no.nav.fia.arbeidsgiver.konfigurasjon.ApplikasjonsHelse
import no.nav.fia.arbeidsgiver.konfigurasjon.Kafka
import no.nav.fia.arbeidsgiver.konfigurasjon.jedisPool
import no.nav.fia.arbeidsgiver.konfigurasjon.plugins.configureMonitoring
import no.nav.fia.arbeidsgiver.konfigurasjon.plugins.configureRouting
import no.nav.fia.arbeidsgiver.konfigurasjon.plugins.configureSecurity
import no.nav.fia.arbeidsgiver.konfigurasjon.plugins.configureSerialization
import no.nav.fia.arbeidsgiver.konfigurasjon.plugins.configureStatusPages
import no.nav.fia.arbeidsgiver.organisasjoner.api.AltinnTilgangerService
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.SpørreundersøkelseService
import no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.SpørreundersøkelseKonsument
import no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.SpørreundersøkelseOppdateringKonsument
import no.nav.fia.arbeidsgiver.valkey.ValkeyService
import java.util.concurrent.TimeUnit

fun main() {
    val applikasjonsHelse = ApplikasjonsHelse()

    val kafka = Kafka()
    val jedisPool = jedisPool()
    val altinnTilgangerService = AltinnTilgangerService()
    val valkeyService = ValkeyService(jedisPool = jedisPool)
    val spørreundersøkelseService = SpørreundersøkelseService(valkeyService = valkeyService)

    SpørreundersøkelseKonsument(
        spørreundersøkelseService = spørreundersøkelseService,
        applikasjonsHelse = applikasjonsHelse,
        kafka = kafka,
    ).run()

    SpørreundersøkelseOppdateringKonsument(
        spørreundersøkelseService = spørreundersøkelseService,
        applikasjonsHelse = applikasjonsHelse,
        kafka = kafka,
    ).run()

    embeddedServer(
        factory = Netty,
        port = 8080,
        host = "0.0.0.0",
    ) {
        configure(
            applikasjonsHelse = applikasjonsHelse,
            altinnTilgangerService = altinnTilgangerService,
            spørreundersøkelseService = spørreundersøkelseService,
        )
    }.also {
        applikasjonsHelse.ready = true
        Runtime.getRuntime().addShutdownHook(
            Thread {
                applikasjonsHelse.ready = false
                applikasjonsHelse.alive = false
                jedisPool.close()
                it.stop(3, 5, TimeUnit.SECONDS)
            },
        )
    }.start(wait = true)
}

fun Application.configure(
    applikasjonsHelse: ApplikasjonsHelse,
    altinnTilgangerService: AltinnTilgangerService,
    spørreundersøkelseService: SpørreundersøkelseService,
) {
    configureMonitoring()
    configureSerialization()
    configureSecurity()
    configureStatusPages()
    configureRouting(
        applikasjonsHelse = applikasjonsHelse,
        altinnTilgangerService = altinnTilgangerService,
        spørreundersøkelseService = spørreundersøkelseService,
    )
}
