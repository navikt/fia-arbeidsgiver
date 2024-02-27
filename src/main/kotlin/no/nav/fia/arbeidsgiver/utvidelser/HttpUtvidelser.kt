package no.nav.fia.arbeidsgiver.utvidelser

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import no.nav.fia.arbeidsgiver.samarbeidsstatus.api.ORGNR

private fun removeBearerPrefix(bearer: String?) = bearer?.split(" ")?.get(1)

fun ApplicationRequest.hentToken() = removeBearerPrefix(this.headers[HttpHeaders.Authorization])
fun ApplicationRequest.tokenSubject() = call.principal<JWTPrincipal>()?.get("pid")

val ApplicationCall.orgnr
    get() = this.parameters[ORGNR]
