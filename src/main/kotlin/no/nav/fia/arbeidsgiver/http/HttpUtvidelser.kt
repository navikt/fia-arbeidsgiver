package no.nav.fia.arbeidsgiver.http

import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.ApplicationRequest

private fun removeBearerPrefix(bearer: String?) = bearer?.split(" ")?.get(1)

fun ApplicationRequest.hentToken() = removeBearerPrefix(this.headers[HttpHeaders.Authorization])

fun ApplicationRequest.fnrFraClaim() = call.principal<JWTPrincipal>()?.get("pid")

fun ApplicationRequest.navIdentFraClaim() = call.principal<JWTPrincipal>()?.get("NAVident")

const val ORGNR = "orgnr"
val ApplicationCall.orgnr
    get() = this.parameters[ORGNR]
