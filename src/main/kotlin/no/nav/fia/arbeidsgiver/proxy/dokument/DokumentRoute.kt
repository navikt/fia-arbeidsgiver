package no.nav.fia.arbeidsgiver.proxy.dokument

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.nav.fia.arbeidsgiver.http.Feil
import no.nav.fia.arbeidsgiver.http.hentToken
import no.nav.fia.arbeidsgiver.samarbeidsstatus.api.ORGNR
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.tilUUID
import java.util.UUID

const val FIA_ARBEIDSGIVER_DOKUMENT_PATH = "/fia-arbeidsgiver/dokument"
const val DOKUMENT_ID = "dokumentId"

fun Route.dokument(dokumentService: DokumentService) {

    get(path = "$FIA_ARBEIDSGIVER_DOKUMENT_PATH/{$ORGNR}/{$DOKUMENT_ID}") {
        val token = call.request.hentToken() ?: return@get call.respond(HttpStatusCode.Forbidden)
        val dokumentId = call.dokumentId

        dokumentService.hentDokument(token = token, dokumentId = dokumentId)?.let { dokumentDto ->
            return@get call.respond(
                status = HttpStatusCode.OK,
                message = dokumentDto,
            )
        }
        return@get call.respond(
            status = HttpStatusCode.NotFound,
            message = "Ingen dokument med id '$dokumentId'",
        )
    }
}

internal val ApplicationCall.dokumentId: UUID
    get() = parameters[DOKUMENT_ID]?.tilUUID("dokumentId")
        ?: throw Feil(feilmelding = "Ugyldig eller manglende dokumentId", feilkode = HttpStatusCode.BadRequest)
