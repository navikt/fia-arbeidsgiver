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
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.IdentifiserbartSpørsmål
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.SvarRequest
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.tilSpørsmålsoversiktDto
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.spørreundersøkelseId
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.spørsmålId
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.temaId
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.tilUUID
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.SpørreundersøkelseService
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.spørsmålFraId
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.temaFraSpørsmålId
import sesjonId


const val DELTAKER_BASEPATH = "$SPØRREUNDERSØKELSE_PATH/deltaker"

fun Route.spørreundersøkelseDeltaker(spørreundersøkelseService: SpørreundersøkelseService) {
    get("$DELTAKER_BASEPATH/{spørreundersøkelseId}") {
        val spørreundersøkelseId = call.spørreundersøkelseId
        val spørreundersøkelse =
            spørreundersøkelseService.hentePågåendeSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId)

        val førsteTema = spørreundersøkelse.temaMedSpørsmålOgSvaralternativer.first()
        call.respond(
            HttpStatusCode.OK, IdentifiserbartSpørsmål(
                temaId = førsteTema.temaId,
                spørsmålId = førsteTema.spørsmålOgSvaralternativer.first().id.toString()
            )
        )
    }

    get("$DELTAKER_BASEPATH/{spørreundersøkelseId}/{temaId}/{spørsmålId}") {
        val spørreundersøkelseId = call.spørreundersøkelseId
        val spørreundersøkelse =
            spørreundersøkelseService.hentePågåendeSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId)
        val spørsmålId = call.spørsmålId
        val temaId = call.temaId


        val spørsmålMedSvarAlternativer = spørreundersøkelse.temaMedSpørsmålOgSvaralternativer
            .spørsmålFraId(spørsmålId = spørsmålId)

        if (spørreundersøkelse.temaMedSpørsmålOgSvaralternativer
                .temaFraSpørsmålId(spørsmålId = spørsmålId).temaId != temaId
        ) {
            call.application.log.warn("TemaId ikke funnet i spørreundersøkelse $temaId")
            return@get call.respond(HttpStatusCode.NotFound)
        }

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
        val svarId = call.receive(SvarRequest::class).svarId.tilUUID("svarId")

        val spørreundersøkelseId = call.spørreundersøkelseId
        val spørreundersøkelse =
            spørreundersøkelseService.hentePågåendeSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId)
        val spørsmålId = call.spørsmålId
        val sesjonId = call.sesjonId

        if (spørreundersøkelse.temaMedSpørsmålOgSvaralternativer.spørsmålFraId(spørsmålId).svaralternativer.none { it.svarId == svarId })
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
