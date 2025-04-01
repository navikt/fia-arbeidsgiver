package no.nav.fia.arbeidsgiver.samarbeidsstatus.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.nav.fia.arbeidsgiver.samarbeidsstatus.api.dto.SamarbeidsstatusDTO
import no.nav.fia.arbeidsgiver.samarbeidsstatus.api.dto.Samarbeidsstaus
import no.nav.fia.arbeidsgiver.samarbeidsstatus.api.dto.tilSamarbeid
import no.nav.fia.arbeidsgiver.samarbeidsstatus.domene.SamarbeidsstatusService

const val ORGNR = "orgnr"
const val SAMARBEIDSSTATUS_PATH = "/fia-arbeidsgiver/status"

fun Route.samarbeidsstatus(samarbeidsstatusService: SamarbeidsstatusService) {
    get("$SAMARBEIDSSTATUS_PATH/{$ORGNR}") {
        val orgnr = call.parameters["orgnr"] ?: return@get call.respond(HttpStatusCode.BadRequest)
        val iaSakStatus = samarbeidsstatusService.henteSakStatus(orgnr)
        call.respond(
            status = HttpStatusCode.OK,
            message = iaSakStatus?.tilSamarbeid() ?: SamarbeidsstatusDTO(orgnr, Samarbeidsstaus.IKKE_I_SAMARBEID),
        )
    }
}
