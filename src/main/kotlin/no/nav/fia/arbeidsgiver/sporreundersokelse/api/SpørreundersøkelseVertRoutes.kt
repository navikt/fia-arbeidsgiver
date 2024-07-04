package no.nav.fia.arbeidsgiver.sporreundersokelse.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import no.nav.fia.arbeidsgiver.http.Feil
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.TemaSvarStatus
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.hentTemaDto
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.tilSpørsmålsoversiktDto
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.tilTemaOversiktDtoer
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.SpørreundersøkelseService


const val VERT_BASEPATH = "$SPØRREUNDERSØKELSE_PATH/vert"

fun Route.spørreundersøkelseVert(spørreundersøkelseService: SpørreundersøkelseService) {
    get("$VERT_BASEPATH/{spørreundersøkelseId}") {
        val spørreundersøkelseId = call.spørreundersøkelseId
        val spørreundersøkelse = spørreundersøkelseService.hentePågåendeSpørreundersøkelse(
            spørreundersøkelseId = spørreundersøkelseId
        )
        val temaStatus = spørreundersøkelse.temaer.map { tema ->
            TemaSvarStatus(
                temaId = tema.id,
                harÅpnetAlleSpørsmål = tema.spørsmål.all { spørsmål ->
                    spørreundersøkelseService.erSpørsmålÅpent(
                        spørreundersøkelseId = spørreundersøkelseId,
                        temaId = tema.id,
                        spørsmålId = spørsmål.id
                    )
                }
            )
        }
        call.respond(
            HttpStatusCode.OK,
            spørreundersøkelse.tilTemaOversiktDtoer(temaStatus)
        )
    }

    get("$VERT_BASEPATH/{spørreundersøkelseId}/virksomhetsnavn") {
        val spørreundersøkelseId = call.spørreundersøkelseId
        val spørreundersøkelse = spørreundersøkelseService.hentePågåendeSpørreundersøkelse(
            spørreundersøkelseId = spørreundersøkelseId
        )
        call.respond(
            HttpStatusCode.OK,
            spørreundersøkelse.virksomhetsNavn
        )
    }

    get("$VERT_BASEPATH/{spørreundersøkelseId}/tema/{temaId}") {
        val spørreundersøkelseId = call.spørreundersøkelseId
        val spørreundersøkelse = spørreundersøkelseService.hentePågåendeSpørreundersøkelse(
            spørreundersøkelseId = spørreundersøkelseId
        )

        val temaStatus = spørreundersøkelse.temaer.map { tema ->
            TemaSvarStatus(
                temaId = tema.id,
                harÅpnetAlleSpørsmål = tema.spørsmål.all { spørsmål ->
                    spørreundersøkelseService.erSpørsmålÅpent(
                        spørreundersøkelseId = spørreundersøkelseId,
                        temaId = tema.id,
                        spørsmålId = spørsmål.id
                    )
                }
            )
        }

        call.respond(
            HttpStatusCode.OK,
            spørreundersøkelse.hentTemaDto(temaId = call.temaId, temaStatus)
        )
    }

    post("$VERT_BASEPATH/{spørreundersøkelseId}/tema/{temaId}/start") {
        spørreundersøkelseService.åpneTema(spørreundersøkelseId = call.spørreundersøkelseId, temaId = call.temaId)
        call.respond(status = HttpStatusCode.OK, message = Unit)
    }

    post("$VERT_BASEPATH/{spørreundersøkelseId}/tema/{temaId}/avslutt") {
        val spørreundersøkelseId = call.spørreundersøkelseId
        val spørreundersøkelse = spørreundersøkelseService.hentePågåendeSpørreundersøkelse(
            spørreundersøkelseId = spørreundersøkelseId
        )

        spørreundersøkelseService.lukkTema(
            spørreundersøkelseId = spørreundersøkelse.id,
            temaId = call.temaId
        )
        call.respond(
            status = HttpStatusCode.OK,
            message = Unit
        )
    }

    get("$VERT_BASEPATH/{spørreundersøkelseId}/tema/{temaId}/sporsmal/{spørsmålId}") {
        //TODO: Deprecated???
        val spørreundersøkelseId = call.spørreundersøkelseId
        val spørreundersøkelse = spørreundersøkelseService.hentePågåendeSpørreundersøkelse(
            spørreundersøkelseId = spørreundersøkelseId
        )
        val spørsmålId = call.spørsmålId

        spørreundersøkelseService.åpneSpørsmål(spørreundersøkelseId = spørreundersøkelseId, spørsmålId = spørsmålId)

        call.respond(
            HttpStatusCode.OK,
            spørreundersøkelse.tilSpørsmålsoversiktDto(spørsmålId = spørsmålId)
        )
    }

    get("$VERT_BASEPATH/{spørreundersøkelseId}/tema/{temaId}/resultater") {
        call.respond(
            HttpStatusCode.OK, spørreundersøkelseService.hentResultater(
                spørreundersøkelseId = call.spørreundersøkelseId,
                temaId = call.temaId
            )
        )
    }
}

fun Route.spørreundersøkelseVertStatus(
    spørreundersøkelseService: SpørreundersøkelseService,
) {

    get("$VERT_BASEPATH/{spørreundersøkelseId}/antall-fullfort") {
        val spørreundersøkelse =
            spørreundersøkelseService.hentePågåendeSpørreundersøkelse(spørreundersøkelseId = call.spørreundersøkelseId)

        val antallFullført = spørreundersøkelse.temaer.minOf { tema ->
            spørreundersøkelseService.antallSvarPåSpørsmålMedFærrestBesvarelser(tema, spørreundersøkelse)
        }

        call.respond(
            status = HttpStatusCode.OK,
            message = antallFullført
        )
    }

    get("$VERT_BASEPATH/{spørreundersøkelseId}/tema/{temaId}/sporsmal/{spørsmålId}/antall-svar") {
        call.respond(
            HttpStatusCode.OK, spørreundersøkelseService.hentAntallSvar(
                spørreundersøkelseId = call.spørreundersøkelseId,
                spørsmålId = call.spørsmålId
            )
        )
    }

    get("$VERT_BASEPATH/{spørreundersøkelseId}/tema/{temaId}/antall-svar") {
        val spørreundersøkelse =
            spørreundersøkelseService.hentePågåendeSpørreundersøkelse(spørreundersøkelseId = call.spørreundersøkelseId)
        val tema = spørreundersøkelse.temaer.firstOrNull {
            it.id == call.temaId
        } ?: throw Feil(feilmelding = "Fant ikke tema ${call.temaId}", feilkode = HttpStatusCode.NotFound)

        val antallFullførtForTema =
            spørreundersøkelseService.antallSvarPåSpørsmålMedFærrestBesvarelser(tema, spørreundersøkelse)

        call.respond(message = antallFullførtForTema, status = HttpStatusCode.OK)
    }

    get("$VERT_BASEPATH/{spørreundersøkelseId}/antall-deltakere") {
        call.respond(
            HttpStatusCode.OK,
            spørreundersøkelseService.hentAntallDeltakere(spørreundersøkelseId = call.spørreundersøkelseId)
        )
    }
}
