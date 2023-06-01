package no.nav.api

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.persistence.RedisService

const val ORGNR = "orgnr"

fun Route.status(redisService: RedisService) {
    get("status/{$ORGNR}") {
        val orgnr = call.parameters["orgnr"] ?: return@get call.respond(HttpStatusCode.BadRequest)
        val iaSakStatus = redisService.henteSakStatus(orgnr)
        call.respond(iaSakStatus?.tilSamarbeid() ?: IASamarbeidDTO(orgnr, Samarbeid.IKKE_I_SAMARBEID))
    }
}
