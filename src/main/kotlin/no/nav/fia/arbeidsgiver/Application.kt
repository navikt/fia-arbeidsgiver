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
import no.nav.fia.arbeidsgiver.samarbeidsstatus.domene.SamarbeidsstatusService
import no.nav.fia.arbeidsgiver.samarbeidsstatus.kafka.FiaStatusKonsument
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.SpørreundersøkelseService
import no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.SpørreundersøkelseKonsument
import no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.SpørreundersøkelseOppdateringKonsument
import no.nav.fia.arbeidsgiver.valkey.ValkeyService

fun main() {
    val applikasjonsHelse = ApplikasjonsHelse()

    val kafka = Kafka()
    val jedisPool = jedisPool()

    val valkeyService = ValkeyService(jedisPool)
    val spørreundersøkelseService = SpørreundersøkelseService(valkeyService)

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

    FiaStatusKonsument(
        samarbeidsstatusService = SamarbeidsstatusService(valkeyService),
        applikasjonsHelse = applikasjonsHelse,
        kafka = kafka,
    ).run()

    embeddedServer(
        factory = Netty,
        port = 8080,
        host = "0.0.0.0",
    ) {
        configure(
            valkeyService = valkeyService,
            applikasjonsHelse = applikasjonsHelse,
            spørreundersøkelseService = spørreundersøkelseService,
        )
    }.also {
        applikasjonsHelse.ready = true

        Runtime.getRuntime().addShutdownHook(
            Thread {
                applikasjonsHelse.ready = false
                applikasjonsHelse.alive = false
                jedisPool.close()
                it.stop(1000, 5000)
            },
        )
    }.start(wait = true)
}

fun Application.configure(
    valkeyService: ValkeyService,
    applikasjonsHelse: ApplikasjonsHelse,
    spørreundersøkelseService: SpørreundersøkelseService,
) {
    configureMonitoring()
    configureSerialization()
    configureSecurity()
    configureStatusPages()
    configureRouting(
        valkeyService = valkeyService,
        applikasjonsHelse = applikasjonsHelse,
        spørreundersøkelseService = spørreundersøkelseService,
    )
}
