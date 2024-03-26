package no.nav.fia.arbeidsgiver.sporreundersokelse.api.vert

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.SPØRREUNDERSØKELSE_PATH
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.tilSpørsmålsoversiktDto
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.spørreundersøkelseId
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.spørsmålId
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.temaId
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.vert.dto.tilTemaOversiktDto
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.SpørreundersøkelseService
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.TemaMedSpørsmålOgSvaralternativer
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.spørsmålFraId


const val VERT_BASEPATH = "$SPØRREUNDERSØKELSE_PATH/vert/v2"

fun Route.spørreundersøkelseVert(spørreundersøkelseService: SpørreundersøkelseService) {
    get("$VERT_BASEPATH/{spørreundersøkelseId}") {
        val spørreundersøkelseId = call.spørreundersøkelseId
        val spørreundersøkelse = spørreundersøkelseService.hentePågåendeSpørreundersøkelse(
            spørreundersøkelseId = spørreundersøkelseId
        )

        call.respond(
            HttpStatusCode.OK,
            spørreundersøkelse.temaMedSpørsmålOgSvaralternativer.tilTemaOversiktDto()
        )
    }

    get("$VERT_BASEPATH/{spørreundersøkelseId}/tema/{temaId}") {
        val spørreundersøkelseId = call.spørreundersøkelseId
        val spørreundersøkelse = spørreundersøkelseService.hentePågåendeSpørreundersøkelse(
            spørreundersøkelseId = spørreundersøkelseId
        )

        val temaMedSpørsmålOgSvaralternativerIndexedValue: IndexedValue<TemaMedSpørsmålOgSvaralternativer> =
            spørreundersøkelse.temaMedSpørsmålOgSvaralternativer.withIndex().first { it.value.temaId == call.temaId }

        call.respond(
            HttpStatusCode.OK,
            temaMedSpørsmålOgSvaralternativerIndexedValue.value
                .tilTemaOversiktDto(temaMedSpørsmålOgSvaralternativerIndexedValue.index + 1)
        )
    }

    get("$VERT_BASEPATH/{spørreundersøkelseId}/antall-deltakere") {
        call.respond(
            HttpStatusCode.OK,
            spørreundersøkelseService.hentAntallDeltakere(spørreundersøkelseId = call.spørreundersøkelseId)
        )
    }

    get("$VERT_BASEPATH/{spørreundersøkelseId}/{temaId}/{spørsmålId}") {
        val spørreundersøkelseId = call.spørreundersøkelseId
        val spørreundersøkelse = spørreundersøkelseService.hentePågåendeSpørreundersøkelse(
            spørreundersøkelseId = spørreundersøkelseId
        )
        val spørsmålId = call.spørsmålId
        val spørsmålMedSvarAlternativer = spørreundersøkelse.temaMedSpørsmålOgSvaralternativer
            .spørsmålFraId(spørsmålId = spørsmålId)

        spørreundersøkelseService.åpneSpørsmål(spørreundersøkelseId = spørreundersøkelseId, spørsmålId = spørsmålId)

        call.respond(
            HttpStatusCode.OK,
            spørsmålMedSvarAlternativer.tilSpørsmålsoversiktDto(spørreundersøkelse = spørreundersøkelse)
        )
    }

    get("$VERT_BASEPATH/{spørreundersøkelseId}/{temaId}/{spørsmålId}/antall-svar") {
        call.respond(
            HttpStatusCode.OK, spørreundersøkelseService.hentAntallSvar(
                spørreundersøkelseId = call.spørreundersøkelseId,
                spørsmålId = call.spørsmålId
            )
        )
    }
}
