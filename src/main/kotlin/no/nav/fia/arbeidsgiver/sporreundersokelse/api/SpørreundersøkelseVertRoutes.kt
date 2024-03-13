package no.nav.fia.arbeidsgiver.sporreundersokelse.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.fia.arbeidsgiver.http.Feil
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.SpørsmålsoversiktDto
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.TemaOversiktDto
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.SpørreundersøkelseService
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.Tema
import no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.dto.SvaralternativDto


const val VERT_BASEPATH = "$SPØRREUNDERSØKELSE_PATH/vert/v2/"

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
            SpørsmålsoversiktDto(
                spørsmålTekst = spørsmålMedSvarAlternativer.spørsmål,
                svaralternativer = spørsmålMedSvarAlternativer.svaralternativer.map { SvaralternativDto(
                    svarId = it.svarId.toString(),
                    svartekst = it.svartekst
                )},
                nesteId = nesteSpørsmålDTO.nesteSpørsmålId,
                nesteType = if (nesteSpørsmålDTO.nesteSpørsmålId != null ) "SPØRSMÅL" else "FERDIG",
                forrigeId = nesteSpørsmålDTO.forrigeSpørsmålId,
                forrigeType = if (nesteSpørsmålDTO.forrigeSpørsmålId != null ) "SPØRSMÅL" else "OVERSIKT",
            )
        )
    }

    post("$VERT_BASEPATH/{spørreundersøkelseId}/{temaId}/{spørsmålId}/status") {
        call.respond(HttpStatusCode.OK, spørreundersøkelseService.hentAntallSvar(
            spørreundersøkelseId = call.spørreundersøkelseId,
            spørsmålId = call.spørsmålId
        ))
    }
}

private val ApplicationCall.spørreundersøkelseId
    get() =
        parameters["spørreundersøkelseId"]?.tilUUID("spørreundersøkelseId")
            ?: throw Feil(feilmelding = "Mangler spørreundersøkelseId", feilkode = HttpStatusCode.BadRequest)

private val ApplicationCall.tema
    get() =
        parameters["temaId"]?.let { Tema.valueOf(it) }
            ?: throw Feil(feilmelding = "Mangler temaId", feilkode = HttpStatusCode.BadRequest)

private val ApplicationCall.spørsmålId
    get() =
        parameters["spørsmålId"]?.tilUUID("spørsmålId")
            ?: throw Feil(feilmelding = "Mangler spørsmålId", feilkode = HttpStatusCode.BadRequest)
