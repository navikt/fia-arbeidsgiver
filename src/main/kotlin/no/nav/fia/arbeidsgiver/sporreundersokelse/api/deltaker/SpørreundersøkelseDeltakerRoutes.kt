package no.nav.fia.arbeidsgiver.sporreundersokelse.api.deltaker

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.fia.arbeidsgiver.http.Feil
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.SPØRREUNDERSØKELSE_PATH
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.deltaker.dto.StartDto
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.DeltakerhandlingRequest
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.spørreundersøkelseId
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.spørsmålId
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.tema
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.tilSpørsmålsoversiktDto
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.tilUUID
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.SpørreundersøkelseService


const val DELTAKER_BASEPATH = "$SPØRREUNDERSØKELSE_PATH/deltaker"

fun Route.spørreundersøkelseDeltaker(spørreundersøkelseService: SpørreundersøkelseService) {
    post("$DELTAKER_BASEPATH/{spørreundersøkelseId}") {
        val deltakerhandlingRequest = call.receive<DeltakerhandlingRequest>()
        val sesjonsId = deltakerhandlingRequest.sesjonsId.tilUUID("sesjonsId")
        val spørreundersøkelseId = call.spørreundersøkelseId

        if (spørreundersøkelseService.henteSpørreundersøkelseIdFraSesjon(sesjonsId) != spørreundersøkelseId)
            throw Feil(feilmelding = "Ugyldig sesjonsId", feilkode = HttpStatusCode.Forbidden)

        val spørreundersøkelse = spørreundersøkelseService.hentePågåendeSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId)

        val førsteTema = spørreundersøkelse.temaMedSpørsmålOgSvaralternativer.first().tema
        call.respond(HttpStatusCode.OK, StartDto(
            temaId = førsteTema,
            spørsmålId = spørreundersøkelse.hentAlleSpørsmålITema(førsteTema).first().id.toString()
        ))
    }

    post("$DELTAKER_BASEPATH/{spørreundersøkelseId}/{temaId}/{spørsmålId}") {
        val deltakerhandlingRequest = call.receive<DeltakerhandlingRequest>()
        val sesjonsId = deltakerhandlingRequest.sesjonsId.tilUUID("sesjonsId")
        val spørreundersøkelseId = call.spørreundersøkelseId

        if (spørreundersøkelseService.henteSpørreundersøkelseIdFraSesjon(sesjonsId) != spørreundersøkelseId)
            throw Feil(feilmelding = "Ugyldig sesjonsId", feilkode = HttpStatusCode.Forbidden)

        val spørreundersøkelse = spørreundersøkelseService.hentePågåendeSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId)
        val tema = call.tema
        val spørsmålId = call.spørsmålId

        if (!spørreundersøkelseService.erSpørsmålÅpent(spørreundersøkelseId = spørreundersøkelseId, spørsmålId = spørsmålId)) {
            return@post call.respond(HttpStatusCode.Accepted)
        }

        val spørsmålMedSvarAlternativer = spørreundersøkelse.spørsmålFraId(tema = tema, spørsmålId = spørsmålId)
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
}
