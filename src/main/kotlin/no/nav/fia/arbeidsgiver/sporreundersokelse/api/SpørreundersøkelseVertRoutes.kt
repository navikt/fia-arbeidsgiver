package no.nav.fia.arbeidsgiver.sporreundersokelse.api

import ia.felles.integrasjoner.kafkameldinger.SpørreundersøkelseStatus
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import no.nav.fia.arbeidsgiver.http.Feil
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.TemaSvarStatus
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.tilTemaDto
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.tilTemaDtoer
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.SpørreundersøkelseService


const val VERT_BASEPATH = "$SPØRREUNDERSØKELSE_PATH/vert"

fun Route.spørreundersøkelseVert(spørreundersøkelseService: SpørreundersøkelseService) {
    get("$VERT_BASEPATH/{spørreundersøkelseId}/oversikt") {
        val spørreundersøkelseId = call.spørreundersøkelseId
        val spørreundersøkelse = spørreundersøkelseService.hentSpørreundersøkelseSomVert(
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
                },
                erStengt = spørreundersøkelseService.erTemaStengt(
                    spørreundersøkelseId = spørreundersøkelseId,
                    temaId = tema.id
                )
            )
        }
        call.respond(
            HttpStatusCode.OK,
            spørreundersøkelse.tilTemaDtoer(temaStatus)
        )
    }

    get("$VERT_BASEPATH/{spørreundersøkelseId}/virksomhetsnavn") {
        val spørreundersøkelseId = call.spørreundersøkelseId
        val spørreundersøkelse = spørreundersøkelseService.hentSpørreundersøkelseSomVert(
            spørreundersøkelseId = spørreundersøkelseId
        )
        call.respond(
            HttpStatusCode.OK,
            spørreundersøkelse.virksomhetsNavn
        )
    }

    get("$VERT_BASEPATH/{spørreundersøkelseId}/tema/{temaId}") {
        val spørreundersøkelseId = call.spørreundersøkelseId
        val spørreundersøkelse = spørreundersøkelseService.hentSpørreundersøkelseSomVert(
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
                },
                erStengt = spørreundersøkelseService.erTemaStengt(
                    spørreundersøkelseId = spørreundersøkelseId,
                    temaId = tema.id
                )
            )
        }

        call.respond(
            HttpStatusCode.OK,
            spørreundersøkelse.tilTemaDto(temaId = call.temaId, temaStatus)
        )
    }

    post("$VERT_BASEPATH/{spørreundersøkelseId}/tema/{temaId}/start") {
        spørreundersøkelseService.åpneTema(spørreundersøkelseId = call.spørreundersøkelseId, temaId = call.temaId)
        call.respond(status = HttpStatusCode.OK, message = Unit)
    }

    post("$VERT_BASEPATH/{spørreundersøkelseId}/tema/{temaId}/avslutt") {
        val spørreundersøkelseId = call.spørreundersøkelseId
        val spørreundersøkelse = spørreundersøkelseService.hentSpørreundersøkelseSomVert(
            spørreundersøkelseId = spørreundersøkelseId
        )

        if (spørreundersøkelse.status == SpørreundersøkelseStatus.AVSLUTTET)
            return@post call.respond(
                status = HttpStatusCode.Accepted,
                message = "Spørreundersøkelse er allerede avsluttet"
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
            spørreundersøkelseService.hentSpørreundersøkelseSomVert(spørreundersøkelseId = call.spørreundersøkelseId)

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
            spørreundersøkelseService.hentSpørreundersøkelseSomVert(spørreundersøkelseId = call.spørreundersøkelseId)
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
