package no.nav.fia.arbeidsgiver.proxy.dokument

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

const val ORGNR = "orgnr"
const val DOKUMENT_PATH = "/fia-arbeidsgiver/dokument"

fun Route.dokument(dokumentService: DokumentService) {
    get(path = "$DOKUMENT_PATH/{$ORGNR}") {
        val orgnr = call.parameters["orgnr"] ?: return@get call.respond(
            status = HttpStatusCode.BadRequest,
            message = "Ugyldig eller manglende orgnr",
        )

        val dokumenter = dokumentService.hentDokumenter(orgnr = orgnr)
        call.respond(
            status = HttpStatusCode.OK,
            message = dokumenter,
        )
    }
}
