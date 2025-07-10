package no.nav.fia.arbeidsgiver.proxy.dokument

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.nav.fia.arbeidsgiver.http.hentToken

const val ORGNR = "orgnr"
const val DOKUMENT_PATH = "/fia-arbeidsgiver/dokument"

fun Route.dokument(dokumentService: DokumentService) {
    get(path = "$DOKUMENT_PATH/{$ORGNR}") {
        val token = call.request.hentToken() ?: return@get call.respond(HttpStatusCode.Forbidden)
        val orgnr = call.parameters["orgnr"] ?: return@get call.respond(
            status = HttpStatusCode.BadRequest,
            message = "Ugyldig eller manglende orgnr",
        )

        val dokumenter = dokumentService.hentDokumenter(
            orgnr = orgnr,
            token = token,
        )
        call.respond(
            status = HttpStatusCode.OK,
            message = dokumenter,
        )
    }
}
