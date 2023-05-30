package no.nav.plugins

import io.ktor.server.routing.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import no.nav.api.helse
import no.nav.api.status

fun Application.configureRouting() {
    routing {
        helse()
        authenticate("tokenx") {
//            medVerifisertAltinnTilgang {
                status()
//            }
        }
    }
}

fun Route.medVerifisertAltinnTilgang(authorizedRoutes: Route.() -> Unit) = createChild(selector).apply {
    install(AuthorizationPlugin)
    authorizedRoutes()
}
