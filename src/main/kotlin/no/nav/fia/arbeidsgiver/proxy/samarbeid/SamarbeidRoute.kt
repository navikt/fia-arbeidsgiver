package no.nav.fia.arbeidsgiver.proxy.samarbeid

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.nav.fia.arbeidsgiver.http.ORGNR
import no.nav.fia.arbeidsgiver.http.hentToken

const val FIA_ARBEIDSGIVER_SAMARBEID_PATH = "/api/samarbeid"

fun Route.samarbeid(samarbeidService: SamarbeidService) {
    get(path = "$FIA_ARBEIDSGIVER_SAMARBEID_PATH/{$ORGNR}") {
        val token = call.request.hentToken() ?: return@get call.respond(HttpStatusCode.Forbidden)
        val orgnr = call.parameters[ORGNR] ?: return@get call.respond(HttpStatusCode.BadRequest)

        samarbeidService.hentSamarbeidMedDokumenter(token = token, orgnr = orgnr).let { listeAvSamarbeid ->
            return@get call.respond(
                status = HttpStatusCode.OK,
                message = listeAvSamarbeid,
            )
        }
    }
}
