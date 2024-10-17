package no.nav.fia.arbeidsgiver.konfigurasjon.plugins

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import no.nav.fia.arbeidsgiver.http.Feil

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<Feil> { call, cause ->
            call.application.log.warn(cause.feilmelding, cause.opprinneligException)
            call.respond(cause.feilkode)
        }
        exception<Throwable> { call: ApplicationCall, cause ->
            call.application.log.error("Uhåndtert feil", cause)
            call.respond(HttpStatusCode.InternalServerError)
        }
    }
}
