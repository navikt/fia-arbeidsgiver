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
import no.nav.fia.arbeidsgiver.konfigurasjon.ApplikasjonsHelse
import no.nav.fia.arbeidsgiver.organisasjoner.api.organisasjoner
import no.nav.fia.arbeidsgiver.proxy.dokument.DokumentService
import no.nav.fia.arbeidsgiver.proxy.dokument.dokument
import no.nav.fia.arbeidsgiver.proxy.samarbeid.SamarbeidService
import no.nav.fia.arbeidsgiver.proxy.samarbeid.samarbeid
import no.nav.fia.arbeidsgiver.samarbeidsstatus.api.AltinnTilgangerService
import no.nav.fia.arbeidsgiver.samarbeidsstatus.api.AltinnTilgangerService.Companion.ENKELRETTIGHET_FOREBYGGE_FRAVÆR_IA_SAMARBEID
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
    spørreundersøkelseService: SpørreundersøkelseService,
) {
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
                medUthentingAvOrganisasjonerIAltinn(altinnTilgangerService = altinnTilgangerService) {
                    organisasjoner()
                    // sjekker også at routes har 'orgnr' som path parameter
                    medVerifisertTilgangTilEnkeltrettighetForOrgnr(
                        enkeltrettighet = ENKELRETTIGHET_FOREBYGGE_FRAVÆR_IA_SAMARBEID,
                    ) {
                        samarbeidsstatus(samarbeidsstatusService = SamarbeidsstatusService(valkeyService = valkeyService))
                        dokument(dokumentService = DokumentService())
                        samarbeid(samarbeidService = SamarbeidService())
                    }
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

fun Route.medUthentingAvOrganisasjonerIAltinn(
    altinnTilgangerService: AltinnTilgangerService,
    authorizedRoutes: Route.() -> Unit,
) = (this as RoutingNode).createChild(selector).apply {
    install(UthentingAvOrganisasjonerIAltinnPlugin(altinnTilgangerService = altinnTilgangerService))
    authorizedRoutes()
}

fun Route.medVerifisertTilgangTilEnkeltrettighetForOrgnr(
    enkeltrettighet: String,
    authorizedRoutes: Route.() -> Unit,
) = (this as RoutingNode).createChild(selector).apply {
    install(AltinnAuthorizationPlugin(enkelrettighet = enkeltrettighet))
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
