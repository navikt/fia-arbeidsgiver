package no.nav.fia.arbeidsgiver.sporreundersokelse.api.deltaker

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.application.log
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import no.nav.fia.arbeidsgiver.http.Feil
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.SPØRREUNDERSØKELSE_PATH
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.deltaker.dto.StartDto
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.SvarRequest
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.spørreundersøkelseId
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.spørsmålId
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.tema
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.tilSpørsmålsoversiktDto
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.tilUUID
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.SpørreundersøkelseService


const val DELTAKER_BASEPATH = "$SPØRREUNDERSØKELSE_PATH/deltaker"

fun Route.spørreundersøkelseDeltaker(spørreundersøkelseService: SpørreundersøkelseService) {
    get("$DELTAKER_BASEPATH/{spørreundersøkelseId}") {
        val spørreundersøkelseId = call.spørreundersøkelseId
        val spørreundersøkelse = spørreundersøkelseService.hentePågåendeSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId)

        val førsteTema = spørreundersøkelse.temaMedSpørsmålOgSvaralternativer.first().tema
        call.respond(HttpStatusCode.OK, StartDto(
            temaId = førsteTema,
            spørsmålId = spørreundersøkelse.hentAlleSpørsmålITema(førsteTema).first().id.toString()
        ))
    }

    get("$DELTAKER_BASEPATH/{spørreundersøkelseId}/{temaId}/{spørsmålId}") {
        val spørreundersøkelseId = call.spørreundersøkelseId
        val spørreundersøkelse = spørreundersøkelseService.hentePågåendeSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId)
        val tema = call.tema
        val spørsmålId = call.spørsmålId

        if (!spørreundersøkelseService.erSpørsmålÅpent(spørreundersøkelseId = spørreundersøkelseId, spørsmålId = spørsmålId)) {
            return@get call.respond(HttpStatusCode.Accepted)
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

    post("$DELTAKER_BASEPATH/{spørreundersøkelseId}/{temaId}/{spørsmålId}") {
        // -- TODO: lag test for denne
        val svarRequest = call.receive(SvarRequest::class)
        val spørreundersøkelseId = svarRequest.spørreundersøkelseId.tilUUID("spørreundersøkelseId")
        val spørsmålId = svarRequest.spørsmålId.tilUUID("spørsmålId")
        val svarId = svarRequest.svarId.tilUUID("svarId")
        val spørreundersøkelse = spørreundersøkelseService.hentePågåendeSpørreundersøkelse(spørreundersøkelseId)

        val spørsmål = spørreundersøkelse.spørsmålOgSvaralternativer.firstOrNull { it.id == spørsmålId }
            ?: throw Feil(feilmelding = "Ukjent spørsmål ($spørsmålId)", feilkode = HttpStatusCode.Forbidden)

        if (spørsmål.svaralternativer.none { it.svarId == svarId })
            throw Feil(feilmelding = "Ukjent svar ($svarId)", feilkode = HttpStatusCode.Forbidden)

        call.application.log.info("Har fått inn svar $svarId")
        spørreundersøkelseService.sendSvar(
            spørreundersøkelseId = spørreundersøkelseId,
            sesjonsId = svarRequest.sesjonsId.tilUUID("sesjonsId"),
            spørsmålId = spørsmålId,
            svarId = svarId,
        )

        call.respond(
            HttpStatusCode.OK,
        )
    }
}
