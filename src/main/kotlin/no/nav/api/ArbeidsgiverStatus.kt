package no.nav.api

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.status() {
    get("status") {
        call.application.log.info("Hei fra backend")
        call.respond("Hei")
    }
}
