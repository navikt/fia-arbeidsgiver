package no.nav.fia.arbeidsgiver.konfigurasjon.plugins

import VerifisertSesjonsId
import VerifisertVertId
import io.ktor.server.routing.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import no.nav.fia.arbeidsgiver.http.helse
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.spørreundersøkelse
import no.nav.fia.arbeidsgiver.samarbeidsstatus.api.samarbeidsstatus
import no.nav.fia.arbeidsgiver.redis.RedisService
import no.nav.fia.arbeidsgiver.samarbeidsstatus.domene.SamarbeidsstatusService
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.deltaker.spørreundersøkelseDeltaker
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.vert.spørreundersøkelseVert
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.SpørreundersøkelseService

fun Application.configureRouting(redisService: RedisService) {
    val spørreundersøkelseService = SpørreundersøkelseService(redisService)
    routing {
        helse()
        spørreundersøkelse(spørreundersøkelseService = spørreundersøkelseService)

        medVerifisertSesjonsId(spørreundersøkelseService = spørreundersøkelseService) {
            spørreundersøkelseDeltaker(spørreundersøkelseService = spørreundersøkelseService)
        }

        medVerifisertVertsId(spørreundersøkelseService = spørreundersøkelseService) {
            spørreundersøkelseVert(spørreundersøkelseService = spørreundersøkelseService)
        }

        authenticate("tokenx") {
            auditLogged {
                medVerifisertAltinnTilgang {
                    samarbeidsstatus(samarbeidsstatusService = SamarbeidsstatusService(redisService = redisService))
                }
            }
        }
    }
}

fun Route.auditLogged(authorizedRoutes: Route.() -> Unit) = createChild(selector).apply {
    install(AuditLogged)
    authorizedRoutes()
}

fun Route.medVerifisertAltinnTilgang(authorizedRoutes: Route.() -> Unit) = createChild(selector).apply {
    install(AuthorizationPlugin)
    authorizedRoutes()
}

fun Route.medVerifisertVertsId(
    spørreundersøkelseService: SpørreundersøkelseService,
    authorizedRoutes: Route.() -> Unit) = createChild(CustomSelector()).apply {
    install(VerifisertVertId(spørreundersøkelseService = spørreundersøkelseService))
    authorizedRoutes()
}

fun Route.medVerifisertSesjonsId(
    spørreundersøkelseService: SpørreundersøkelseService,
    authorizedRoutes: Route.() -> Unit) = createChild(CustomSelector()).apply {
    install(VerifisertSesjonsId(spørreundersøkelseService = spørreundersøkelseService))
    authorizedRoutes()
}

private class CustomSelector : RouteSelector() {
    override fun evaluate(context: RoutingResolveContext, segmentIndex: Int) = RouteSelectorEvaluation.Transparent
}