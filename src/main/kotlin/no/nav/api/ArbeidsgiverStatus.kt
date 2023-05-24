package no.nav.api

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

const val ORGNR = "orgnr"

fun Route.status() {
    get("status/{$ORGNR}") {
        val orgnr = call.parameters["orgnr"] ?: return@get call.respond(HttpStatusCode.BadRequest)
        call.application.log.info("Hei fra backend med orgnr $orgnr")
        call.respond(orgnr)
    }
}
