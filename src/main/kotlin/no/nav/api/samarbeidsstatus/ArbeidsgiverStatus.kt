package no.nav.api.samarbeidsstatus

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.persistence.RedisService

const val ORGNR = "orgnr"
const val STATUS_PATH = "/fia-arbeidsgiver/status"

fun Route.status(redisService: RedisService) {
    get("$STATUS_PATH/{$ORGNR}") {
        val orgnr = call.parameters["orgnr"] ?: return@get call.respond(HttpStatusCode.BadRequest)
        val iaSakStatus = redisService.henteSakStatus(orgnr)
        call.respond(
            status = HttpStatusCode.OK,
            message = iaSakStatus?.tilSamarbeid() ?: IASamarbeidDTO(orgnr, Samarbeid.IKKE_I_SAMARBEID)
        )
    }
}
