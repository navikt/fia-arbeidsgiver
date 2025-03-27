package no.nav.fia.arbeidsgiver.konfigurasjon.plugins

import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.Route
import io.ktor.server.routing.RouteSelector
import io.ktor.server.routing.RouteSelectorEvaluation
import io.ktor.server.routing.RoutingNode
import io.ktor.server.routing.RoutingResolveContext
import io.ktor.server.routing.routing
import no.nav.fia.arbeidsgiver.http.helse
import no.nav.fia.arbeidsgiver.konfigurasjon.AltinnTilgangerService
import no.nav.fia.arbeidsgiver.konfigurasjon.ApplikasjonsHelse
import no.nav.fia.arbeidsgiver.samarbeidsstatus.api.samarbeidsstatus
import no.nav.fia.arbeidsgiver.samarbeidsstatus.domene.SamarbeidsstatusService
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.spørreundersøkelse
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.spørreundersøkelseDeltaker
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.spørreundersøkelseVert
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.spørreundersøkelseVertStatus
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.SpørreundersøkelseService
import no.nav.fia.arbeidsgiver.valkey.ValkeyService

fun Application.configureRouting(
    valkeyService: ValkeyService,
    applikasjonsHelse: ApplikasjonsHelse,
    altinnTilgangerService: AltinnTilgangerService,
) {
    val spørreundersøkelseService = SpørreundersøkelseService(valkeyService)
    routing {
        spørreundersøkelse(spørreundersøkelseService = spørreundersøkelseService)

        medVerifisertSesjonId(spørreundersøkelseService = spørreundersøkelseService) {
            spørreundersøkelseDeltaker(spørreundersøkelseService = spørreundersøkelseService)
        }

        authenticate("azure") {
            auditLogged(spørreundersøkelseService = spørreundersøkelseService) {
                spørreundersøkelseVert(spørreundersøkelseService = spørreundersøkelseService)
            }
            spørreundersøkelseVertStatus(spørreundersøkelseService = spørreundersøkelseService)
        }

        authenticate("tokenx") {
            auditLogged(spørreundersøkelseService = spørreundersøkelseService) {
                medAltinnTilgang(
                    altinnTilgangerService = altinnTilgangerService,
                ) {
                    samarbeidsstatus(samarbeidsstatusService = SamarbeidsstatusService(valkeyService = valkeyService))
                }
            }
        }

        helse(lever = { applikasjonsHelse.alive }, klar = { applikasjonsHelse.ready })
    }
}

fun Route.auditLogged(
    spørreundersøkelseService: SpørreundersøkelseService,
    authorizedRoutes: Route.() -> Unit,
) = (this as RoutingNode).createChild(selector).apply {
    install(AuditLogged(spørreundersøkelseService = spørreundersøkelseService))
    authorizedRoutes()
}

fun Route.medAltinnTilgang(
    altinnTilgangerService: AltinnTilgangerService,
    authorizedRoutes: Route.() -> Unit,
) = (this as RoutingNode).createChild(selector).apply {
    install(AltinnAuthorizationPlugin(altinnTilgangerService = altinnTilgangerService))
    authorizedRoutes()
}

fun Route.medVerifisertSesjonId(
    spørreundersøkelseService: SpørreundersøkelseService,
    authorizedRoutes: Route.() -> Unit,
) = createChild(CustomSelector()).apply {
    install(VerifisertSesjonId(spørreundersøkelseService = spørreundersøkelseService))
    authorizedRoutes()
}

private class CustomSelector : RouteSelector() {
    override suspend fun evaluate(
        context: RoutingResolveContext,
        segmentIndex: Int,
    ) = RouteSelectorEvaluation.Transparent
}
