package no.nav.fia.arbeidsgiver.samarbeidsstatus.api

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.fia.arbeidsgiver.persistence.RedisService
import no.nav.fia.arbeidsgiver.samarbeidsstatus.api.dto.SamarbeidsstatusDTO
import no.nav.fia.arbeidsgiver.samarbeidsstatus.api.dto.Samarbeidsstaus
import no.nav.fia.arbeidsgiver.samarbeidsstatus.api.dto.tilSamarbeid

const val ORGNR = "orgnr"
const val SAMARBEIDSSTATUS_PATH = "/fia-arbeidsgiver/status"

fun Route.samarbeidsstatus(redisService: RedisService) {
    get("$SAMARBEIDSSTATUS_PATH/{$ORGNR}") {
        val orgnr = call.parameters["orgnr"] ?: return@get call.respond(HttpStatusCode.BadRequest)
        val iaSakStatus = redisService.henteSakStatus(orgnr)
        call.respond(
            status = HttpStatusCode.OK,
            message = iaSakStatus?.tilSamarbeid() ?: SamarbeidsstatusDTO(orgnr, Samarbeidsstaus.IKKE_I_SAMARBEID)
        )
    }
}
