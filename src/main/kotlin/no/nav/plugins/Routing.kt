package no.nav.plugins

import io.ktor.server.routing.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import no.nav.api.helse
import no.nav.api.status
import no.nav.persistence.RedisService

fun Application.configureRouting(redisService: RedisService) {
    routing {
        helse()
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
