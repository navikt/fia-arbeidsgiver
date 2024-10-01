package no.nav.fia.arbeidsgiver.http

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.helse(
    lever: () -> Boolean,
    klar: () -> Boolean
) {
    get("internal/isalive") {
        if (lever()) {
            call.respondText("Alive")
        } else {
            call.respondText("Not alive", status = HttpStatusCode.InternalServerError)
        }
    }
    get("internal/isready") {
        if (klar()) {
            call.respondText("Ready")
        } else {
            call.respondText("Not ready", status = HttpStatusCode.InternalServerError)
        }
    }
}
