package no.nav.fia.arbeidsgiver.sporreundersokelse.api.deltaker

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import no.nav.fia.arbeidsgiver.http.Feil
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.SPØRREUNDERSØKELSE_PATH
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.IdentifiserbartSpørsmål
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.tilSpørsmålsoversiktDto
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.NySvarRequest
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.spørreundersøkelseId
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.spørsmålId
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.tema
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.tilUUID
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.SpørreundersøkelseService
import sesjonId


const val DELTAKER_BASEPATH = "$SPØRREUNDERSØKELSE_PATH/deltaker"

fun Route.spørreundersøkelseDeltaker(spørreundersøkelseService: SpørreundersøkelseService) {
    get("$DELTAKER_BASEPATH/{spørreundersøkelseId}") {
        val spørreundersøkelseId = call.spørreundersøkelseId
        val spørreundersøkelse =
            spørreundersøkelseService.hentePågåendeSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId)

        val førsteTema = spørreundersøkelse.temaMedSpørsmålOgSvaralternativer.first().tema
        call.respond(
            HttpStatusCode.OK, IdentifiserbartSpørsmål(
                tema = førsteTema,
                spørsmålId = spørreundersøkelse.hentAlleSpørsmålITema(førsteTema).first().id.toString()
            )
        )
    }

    get("$DELTAKER_BASEPATH/{spørreundersøkelseId}/{temaId}/{spørsmålId}") {
        val spørreundersøkelseId = call.spørreundersøkelseId
        val spørreundersøkelse =
            spørreundersøkelseService.hentePågåendeSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId)
        val tema = call.tema
        val spørsmålId = call.spørsmålId

        val spørsmålMedSvarAlternativer = spørreundersøkelse.spørsmålFraId(tema = tema, spørsmålId = spørsmålId)

        if (!spørreundersøkelseService.erSpørsmålÅpent(
                spørreundersøkelseId = spørreundersøkelseId,
                spørsmålId = spørsmålMedSvarAlternativer.id
            )
        ) {
            return@get call.respond(HttpStatusCode.Accepted)
        }

        call.respond(
            HttpStatusCode.OK,
            spørsmålMedSvarAlternativer.tilSpørsmålsoversiktDto(spørreundersøkelse = spørreundersøkelse)
        )
    }

    post("$DELTAKER_BASEPATH/{spørreundersøkelseId}/{temaId}/{spørsmålId}/svar") {
        // -- TODO: lag test for denne
        val svarId = call.receive(NySvarRequest::class).svarId.tilUUID("svarId")

        val spørreundersøkelseId = call.spørreundersøkelseId
        val spørreundersøkelse =
            spørreundersøkelseService.hentePågåendeSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId)
        val spørsmålId = call.spørsmålId
        val tema = call.tema
        val sesjonId = call.sesjonId

        if (spørreundersøkelse.spørsmålFraId(tema, spørsmålId).svaralternativer.none { it.svarId == svarId })
            throw Feil(feilmelding = "Ukjent svar ($svarId)", feilkode = HttpStatusCode.Forbidden)

        spørreundersøkelseService.sendSvar(
            spørreundersøkelseId = spørreundersøkelseId,
            sesjonsId = sesjonId,
            spørsmålId = spørsmålId,
            svarId = svarId,
        )

        call.respond(
            HttpStatusCode.OK,
        )
    }
}
