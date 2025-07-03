package no.nav.fia.arbeidsgiver.organisasjoner.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.nav.fia.arbeidsgiver.http.hentToken
import no.nav.fia.arbeidsgiver.samarbeidsstatus.api.AltinnTilgangerService

const val ORGANISASJONER_PATH = "/fia-arbeidsgiver/organisasjoner"

fun Route.organisasjoner(altinnTilgangerService: AltinnTilgangerService) {
    get(path = ORGANISASJONER_PATH) {
        val token = call.request.hentToken() ?: return@get call.respond(HttpStatusCode.Forbidden)
        val sthg = altinnTilgangerService.hentAltinnTilganger(token = token)
        call.respond(
            status = HttpStatusCode.OK,
            message = sthg?.hierarki ?: emptyList(),
        )
    }
}
