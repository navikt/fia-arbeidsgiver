package no.nav.fia.arbeidsgiver.plugins

import io.ktor.server.routing.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import no.nav.fia.arbeidsgiver.api.helse
import no.nav.fia.arbeidsgiver.api.sporreundersokelse.spørreundersøkelse
import no.nav.fia.arbeidsgiver.api.samarbeidsstatus.status
import no.nav.fia.arbeidsgiver.persistence.RedisService

fun Application.configureRouting(redisService: RedisService) {
    routing {
        helse()
        spørreundersøkelse(redisService = redisService)
        authenticate("tokenx") {
            auditLogged {
                medVerifisertAltinnTilgang {
                    status(redisService = redisService)
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
