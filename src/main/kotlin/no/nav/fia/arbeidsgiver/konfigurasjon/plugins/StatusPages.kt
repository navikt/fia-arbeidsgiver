package no.nav.fia.arbeidsgiver.konfigurasjon.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
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