package no.nav.api

import com.auth0.jwt.JWT
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.helse() {
    get("internal/isalive") {
        val token = call.request.headers[HttpHeaders.Authorization]
        call.application.log.info("TOKEN: $token")
        call.respond("OK")
    }
    get("internal/isready") {
        call.respond("OK")
    }
}