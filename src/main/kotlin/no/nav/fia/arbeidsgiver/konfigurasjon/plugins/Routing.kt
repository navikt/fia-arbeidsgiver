package no.nav.fia.arbeidsgiver.konfigurasjon.plugins

import VerifisertSesjonId
import VerifisertVertId
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.Route
import io.ktor.server.routing.RouteSelector
import io.ktor.server.routing.RouteSelectorEvaluation
import io.ktor.server.routing.RoutingResolveContext
import io.ktor.server.routing.routing
import no.nav.fia.arbeidsgiver.http.helse
import no.nav.fia.arbeidsgiver.redis.RedisService
import no.nav.fia.arbeidsgiver.samarbeidsstatus.api.samarbeidsstatus
import no.nav.fia.arbeidsgiver.samarbeidsstatus.domene.SamarbeidsstatusService
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.spørreundersøkelseDeltaker
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.spørreundersøkelse
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.spørreundersøkelseVert
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.spørreundersøkelseVertStatus
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.SpørreundersøkelseService

fun Application.configureRouting(redisService: RedisService) {
    val spørreundersøkelseService = SpørreundersøkelseService(redisService)
    routing {
        helse()
        spørreundersøkelse(spørreundersøkelseService = spørreundersøkelseService)

        medVerifisertSesjonId(spørreundersøkelseService = spørreundersøkelseService) {
            spørreundersøkelseDeltaker(spørreundersøkelseService = spørreundersøkelseService)
        }

        authenticate("azure") {
            medVerifisertVertsId(spørreundersøkelseService = spørreundersøkelseService) {
                auditLogged(spørreundersøkelseService = spørreundersøkelseService) {
                    spørreundersøkelseVert(spørreundersøkelseService = spørreundersøkelseService)
                }
                spørreundersøkelseVertStatus(spørreundersøkelseService = spørreundersøkelseService)
            }
        }

        authenticate("tokenx") {
            auditLogged(spørreundersøkelseService = spørreundersøkelseService) {
                medVerifisertAltinnTilgang {
                    samarbeidsstatus(samarbeidsstatusService = SamarbeidsstatusService(redisService = redisService))
                }
            }
        }
    }
}

fun Route.auditLogged(
    spørreundersøkelseService: SpørreundersøkelseService,
    authorizedRoutes: Route.() -> Unit,
) = createChild(selector).apply {
    install(AuditLogged(spørreundersøkelseService = spørreundersøkelseService))
    authorizedRoutes()
}

fun Route.medVerifisertAltinnTilgang(authorizedRoutes: Route.() -> Unit) = createChild(selector).apply {
    install(AuthorizationPlugin)
    authorizedRoutes()
}

fun Route.medVerifisertVertsId(
    spørreundersøkelseService: SpørreundersøkelseService,
    authorizedRoutes: Route.() -> Unit,
) = createChild(CustomSelector()).apply {
    install(VerifisertVertId(spørreundersøkelseService = spørreundersøkelseService))
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
    override fun evaluate(context: RoutingResolveContext, segmentIndex: Int) = RouteSelectorEvaluation.Transparent
}