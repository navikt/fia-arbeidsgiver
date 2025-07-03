package no.nav.fia.arbeidsgiver.organisasjoner.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.nav.fia.arbeidsgiver.konfigurasjon.plugins.AltinnTilgangerKey

const val ORGANISASJONER_PATH = "/fia-arbeidsgiver/organisasjoner"

fun Route.organisasjoner() {
    get(path = ORGANISASJONER_PATH) {
        val tilganger = call.attributes[AltinnTilgangerKey]

        call.respond(
            status = HttpStatusCode.OK,
            message = tilganger.altinnTilganger?.hierarki ?: emptyList(),
        )
    }
}
