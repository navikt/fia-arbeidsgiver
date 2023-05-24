package no.nav.utvidelser

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import no.nav.api.ORGNR

private fun removeBearerPrefix(bearer: String?) = bearer?.split(" ")?.first()

fun ApplicationRequest.hentToken() = removeBearerPrefix(this.headers[HttpHeaders.Authorization])
fun ApplicationRequest.tokenSubject() = call.principal<JWTPrincipal>()?.get("pid")

val ApplicationCall.orgnr
    get() = this.parameters[ORGNR]
