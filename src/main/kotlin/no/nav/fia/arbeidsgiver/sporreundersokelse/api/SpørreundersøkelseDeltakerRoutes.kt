package no.nav.fia.arbeidsgiver.sporreundersokelse.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.log
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import no.nav.fia.arbeidsgiver.http.Feil
import no.nav.fia.arbeidsgiver.konfigurasjon.plugins.sesjonId
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.IdentifiserbartSpørsmålDto
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.SvarRequest
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.tilDeltakerSpørsmål
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.SpørreundersøkelseService
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.spørsmålFraId
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.temaFraSpørsmålId

const val DELTAKER_BASEPATH = "$SPØRREUNDERSØKELSE_PATH/deltaker"

fun Route.spørreundersøkelseDeltaker(spørreundersøkelseService: SpørreundersøkelseService) {
    get("$DELTAKER_BASEPATH/{spørreundersøkelseId}") {
        val spørreundersøkelseId = call.spørreundersøkelseId
        val spørreundersøkelse =
            spørreundersøkelseService.hentePågåendeSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId)

        val førsteÅpneTema = spørreundersøkelse.temaer.first {
            !spørreundersøkelseService.erTemaStengt(spørreundersøkelseId, it.id)
        }

        call.respond(
            status = HttpStatusCode.OK,
            message = IdentifiserbartSpørsmålDto(
                temaId = førsteÅpneTema.id,
                spørsmålId = førsteÅpneTema.spørsmål.first().id.toString(),
            ),
        )
    }

    get("$DELTAKER_BASEPATH/{spørreundersøkelseId}/tema/{temaId}/sporsmal/{spørsmålId}") {
        val spørreundersøkelseId = call.spørreundersøkelseId
        val spørreundersøkelse =
            spørreundersøkelseService.hentePågåendeSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId)
        val spørsmålId = call.spørsmålId
        val temaId = call.temaId

        if (spørreundersøkelse.temaer.temaFraSpørsmålId(spørsmålId = spørsmålId).id != temaId) {
            call.application.log.warn("TemaId ikke funnet i spørreundersøkelse $temaId")
            return@get call.respond(HttpStatusCode.NotFound)
        }

        if (!spørreundersøkelseService.erSpørsmålÅpent(
                spørreundersøkelseId = spørreundersøkelseId,
                temaId = temaId,
                spørsmålId = spørsmålId,
            )
        ) {
            return@get call.respond(HttpStatusCode.Accepted)
        }

        call.respond(
            HttpStatusCode.OK,
            spørreundersøkelse.tilDeltakerSpørsmål(spørsmålId = spørsmålId),
        )
    }

    post("$DELTAKER_BASEPATH/{spørreundersøkelseId}/tema/{temaId}/sporsmal/{spørsmålId}/svar") {
        val svarIder = call.receive(SvarRequest::class).svarIder.map { it.tilUUID("svarId") }

        val spørreundersøkelseId = call.spørreundersøkelseId

        val spørreundersøkelse = try {
            spørreundersøkelseService.hentePågåendeSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId)
        } catch (feil: Feil) {
            throw feil
        }
        val spørsmålId = call.spørsmålId
        val sesjonId = call.sesjonId
        val spørsmål = spørreundersøkelse.temaer.spørsmålFraId(spørsmålId)
        val temaId = call.temaId

        if (spørreundersøkelseService.erAlleTemaerErStengt(spørreundersøkelse)) {
            throw Feil(
                feilmelding = "Alle temaer er stengt for spørreundersøkelse '$spørreundersøkelseId'",
                feilkode = HttpStatusCode.BadRequest,
            )
        }

        if (spørreundersøkelseService.erTemaStengt(spørreundersøkelseId, temaId)) {
            call.application.log.info("Tema '$temaId' er stengt, hent nytt spørsmål")
            call.respond(
                message = "Tema '$temaId' er stengt, hent nytt spørsmål",
                status = HttpStatusCode.SeeOther,
            )
        }

        if (spørsmål.svaralternativer.none { svarIder.contains(it.id) }) {
            throw Feil(
                feilmelding = "Ukjent svar for spørsmålId: ($spørsmålId)",
                feilkode = HttpStatusCode.Forbidden,
            )
        }

        if (svarIder.size > 1 && !spørsmål.flervalg) {
            throw Feil(feilmelding = "Spørsmål er ikke flervalg, id: $spørsmålId", feilkode = HttpStatusCode.BadRequest)
        }

        spørreundersøkelseService.sendSvar(
            spørreundersøkelseId = spørreundersøkelseId,
            sesjonsId = sesjonId,
            spørsmålId = spørsmålId,
            svarIder = svarIder,
        )

        call.respond(
            HttpStatusCode.OK,
        )
    }
}
