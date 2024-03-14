package no.nav.fia.arbeidsgiver.sporreundersokelse.api.vert

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.SPØRREUNDERSØKELSE_PATH
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.spørreundersøkelseId
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.spørsmålId
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.tema
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.tilSpørsmålsoversiktDto
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.vert.dto.TemaOversiktDto
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.SpørreundersøkelseService


const val VERT_BASEPATH = "$SPØRREUNDERSØKELSE_PATH/vert/v2"

fun Route.spørreundersøkelseVert(spørreundersøkelseService: SpørreundersøkelseService) {
    post("$VERT_BASEPATH/{spørreundersøkelseId}") {
        val spørreundersøkelseId = call.spørreundersøkelseId
        val spørreundersøkelse = spørreundersøkelseService.hentePågåendeSpørreundersøkelse(
            spørreundersøkelseId = spørreundersøkelseId
        )

        call.respond(
            HttpStatusCode.OK,
            spørreundersøkelse.temaMedSpørsmålOgSvaralternativer.map {
                TemaOversiktDto(
                    tittel = it.tema.name,
                    temaId = it.tema.name,
                    førsteSpørsmålId = it.spørsmålOgSvaralternativer.first().id.toString()
                )
            }
        )
    }

    post("$VERT_BASEPATH/{spørreundersøkelseId}/status") {
        call.respond(
            HttpStatusCode.OK,
            spørreundersøkelseService.hentAntallDeltakere(spørreundersøkelseId = call.spørreundersøkelseId)
        )
    }

    post("$VERT_BASEPATH/{spørreundersøkelseId}/{temaId}/{spørsmålId}") {
        val spørreundersøkelseId = call.spørreundersøkelseId
        val spørreundersøkelse = spørreundersøkelseService.hentePågåendeSpørreundersøkelse(
            spørreundersøkelseId = spørreundersøkelseId
        )
        val tema = call.tema
        val spørsmålId = call.spørsmålId
        val spørsmålMedSvarAlternativer = spørreundersøkelse.spørsmålFraId(tema = tema, spørsmålId = spørsmålId)

        spørreundersøkelseService.åpneSpørsmål(spørreundersøkelseId = spørreundersøkelseId, spørsmålId = spørsmålId)

        val nesteSpørsmålDTO = spørreundersøkelseService.hentNesteSpørsmål(
            spørreundersøkelseId = spørreundersøkelseId,
            nåværendeSpørsmålId = spørsmålId.toString(),
            tema = tema
        )

        call.respond(
            HttpStatusCode.OK,
            spørsmålMedSvarAlternativer.tilSpørsmålsoversiktDto(nesteSpørsmålDTO = nesteSpørsmålDTO)
        )
    }

    post("$VERT_BASEPATH/{spørreundersøkelseId}/{temaId}/{spørsmålId}/status") {
        call.respond(HttpStatusCode.OK, spørreundersøkelseService.hentAntallSvar(
            spørreundersøkelseId = call.spørreundersøkelseId,
            spørsmålId = call.spørsmålId
        ))
    }
}
