package no.nav.fia.arbeidsgiver.sporreundersokelse.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import no.nav.fia.arbeidsgiver.http.Feil
import no.nav.fia.arbeidsgiver.konfigurasjon.KafkaConfig
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.TemastatusDTO.Status.IKKE_PÅBEGYNT
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.TemastatusDTO.Status.PÅBEGYNT
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.AntallDeltakereDTO
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.AntallSvarDTO
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.BliMedDTO
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.BliMedRequest
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.DeltakerhandlingRequest
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.NesteSpørsmålDTO
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.NesteSpørsmålRequest
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.SpørsmålOgSvaralternativerDTO
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.StartTemaRequest
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.SvarRequest
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.VertshandlingRequest
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.Tema
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.SpørreundersøkelseService
import no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.SpørreundersøkelseSvarDTO
import no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.SpørreundersøkelseSvarProdusent
import java.util.*
import kotlin.IllegalArgumentException
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.AntallSvarPerSpørsmålDTO

const val SPØRREUNDERSØKELSE_PATH = "/fia-arbeidsgiver/sporreundersokelse"

const val BLI_MED_PATH = "$SPØRREUNDERSØKELSE_PATH/bli-med"
const val NESTE_SPØRSMÅL_PATH = "$SPØRREUNDERSØKELSE_PATH/neste-sporsmal"
const val SPØRSMÅL_OG_SVAR_PATH = "$SPØRREUNDERSØKELSE_PATH/sporsmal-og-svar"
const val SVAR_PATH = "$SPØRREUNDERSØKELSE_PATH/svar"

const val VERT_ANTALL_SVAR_PATH = "$SPØRREUNDERSØKELSE_PATH/vert/antall-svar"
const val VERT_NESTE_SPØRSMÅL_PATH = "$SPØRREUNDERSØKELSE_PATH/vert/neste-sporsmal"
const val VERT_SPØRREUNDERSØKELSESTATUS_PATH = "$SPØRREUNDERSØKELSE_PATH/vert/sporreundersokelse-status"
const val VERT_SPØRSMÅL_OG_SVAR_PATH = "$SPØRREUNDERSØKELSE_PATH/vert/sporsmal-og-svar"


@Deprecated("Skal ikke brukes lenger?")
const val TEMASTATUS_PATH = "$SPØRREUNDERSØKELSE_PATH/temastatus"

@Deprecated("Skal erstattes med antall-svar")
const val VERT_ANTALL_DELTAKERE_PATH = "$SPØRREUNDERSØKELSE_PATH/vert/antall-deltakere"

@Deprecated("Skal erstattes med vert/sporsmal-og-svar")
const val VERT_START_TEMA_PATH = "$SPØRREUNDERSØKELSE_PATH/vert/start-tema"

@Deprecated("Skal erstattes med vert/sporsmal-og-svar")
const val VERT_INKREMENTER_SPØRSMÅL_PATH = "$SPØRREUNDERSØKELSE_PATH/vert/inkrementer-sporsmal"

@Deprecated("Mulig denne utgår helt ?")
const val VERT_TEMA_PATH = "$SPØRREUNDERSØKELSE_PATH/vert/temastatus"

fun Route.spørreundersøkelse(spørreundersøkelseService: SpørreundersøkelseService) {
    val spørreundersøkelseSvarProdusent = SpørreundersøkelseSvarProdusent(kafkaConfig = KafkaConfig())
    post(BLI_MED_PATH) {
        val bliMedRequest = call.receive(BliMedRequest::class)

        val spørreundersøkelseId = bliMedRequest.spørreundersøkelseId.tilUUID("spørreundersøkelseId")

        val spørreundersøkelse = spørreundersøkelseService.hentePågåendeSpørreundersøkelse(spørreundersøkelseId)

        val sesjonsId = UUID.randomUUID()
        spørreundersøkelseService.lagreSesjon(sesjonsId, spørreundersøkelse.spørreundersøkelseId)

        val antallDeltakere = spørreundersøkelseService.hentAntallDeltakere(spørreundersøkelse.spørreundersøkelseId)
        spørreundersøkelseService.lagreAntallDeltakere(spørreundersøkelse.spørreundersøkelseId, (antallDeltakere + 1))

        call.respond(
            HttpStatusCode.OK, BliMedDTO(
                spørreundersøkelseId = spørreundersøkelse.spørreundersøkelseId.toString(),
                sesjonsId = sesjonsId.toString()
            )
        )
    }

    post(SPØRSMÅL_OG_SVAR_PATH) {
        val deltakerhandlingRequest = call.receive(DeltakerhandlingRequest::class)

        val spørreundersøkelseId = deltakerhandlingRequest.spørreundersøkelseId.tilUUID("spørreundersøkelseId")
        val sesjonsId = deltakerhandlingRequest.sesjonsId.tilUUID("sesjonsId")

        validerSesjonsId(
            spørreundersøkelseService = spørreundersøkelseService,
            sesjonsId = sesjonsId,
            spørreundersøkelseId = spørreundersøkelseId
        )

        val spørreundersøkelse = spørreundersøkelseService.hentePågåendeSpørreundersøkelse(spørreundersøkelseId)


        call.respond(
            HttpStatusCode.OK,
            SpørsmålOgSvaralternativerDTO.toDto(spørreundersøkelse.spørsmålOgSvaralternativer)
        )
    }

    post(TEMASTATUS_PATH) {
        val request = call.receive(DeltakerhandlingRequest::class)

        val spørreundersøkelseId = request.spørreundersøkelseId.tilUUID("spørreundersøkelseId")
            ?: throw Feil(
                "SpørreundersøkelseId skal aldri kunne være null", feilkode = HttpStatusCode.InternalServerError
            )
        val sesjonsId = request.sesjonsId.tilUUID("sesjonsId")

        validerSesjonsId(
            spørreundersøkelseService = spørreundersøkelseService,
            sesjonsId = sesjonsId,
            spørreundersøkelseId = spørreundersøkelseId
        )

        val temastatus = spørreundersøkelseService.hentTemastatus(
            spørreundersøkelseId,
            request.tema
        ) ?: throw Feil(
            "Finner ikke temastatus på undersøkelse $spørreundersøkelseId",
            feilkode = HttpStatusCode.InternalServerError
        )

        call.respond(HttpStatusCode.OK, temastatus)
    }


    post("$SPØRSMÅL_OG_SVAR_PATH/{spørsmålId}") {
        val deltakerhandlingRequest = call.receive(DeltakerhandlingRequest::class)
        val spørsmålId =
            call.spørsmålId?.tilUUID("spørsmålId") ?: return@post call.respond(HttpStatusCode.BadRequest)
        val spørreundersøkelseId = deltakerhandlingRequest.spørreundersøkelseId.tilUUID("spørreundersøkelseId")
        val sesjonsId = deltakerhandlingRequest.sesjonsId.tilUUID("sesjonsId")

        validerSesjonsId(
            spørreundersøkelseService = spørreundersøkelseService,
            sesjonsId = sesjonsId,
            spørreundersøkelseId = spørreundersøkelseId
        )

        val spørreundersøkelse = spørreundersøkelseService.hentePågåendeSpørreundersøkelse(spørreundersøkelseId)
        val indeksTilSpørsmålId = spørreundersøkelse.indeksFraSpørsmålId(spørsmålId)
        val spørsmålOgSvaralternativer = spørreundersøkelse.spørsmålFraId(spørsmålId)


        val indeksTilSisteSpørsmål = spørreundersøkelse.spørsmålOgSvaralternativer.size - 1
        call.respond(
            HttpStatusCode.OK,
            spørsmålOgSvaralternativer.toFrontendDto(indeksTilSpørsmålId, indeksTilSisteSpørsmål)
        )

    }

    post(NESTE_SPØRSMÅL_PATH) {
        val nesteSpørsmålRequest = call.receive(NesteSpørsmålRequest::class)

        val spørreundersøkelseId =
            nesteSpørsmålRequest.spørreundersøkelseId.tilUUID("spørreundersøkelseId")
        val sesjonsId = nesteSpørsmålRequest.sesjonsId.tilUUID("sesjonsId")

        validerSesjonsId(
            spørreundersøkelseService = spørreundersøkelseService,
            sesjonsId = sesjonsId,
            spørreundersøkelseId = spørreundersøkelseId
        )

        val spørreundersøkelse = spørreundersøkelseService.hentePågåendeSpørreundersøkelse(spørreundersøkelseId)

        val indeksTilNåværendeSpørsmålId = if (nesteSpørsmålRequest.nåværendeSpørsmålId.uppercase() == "START") {
            null
        } else {
            val nåværrendeSpørsmålId = nesteSpørsmålRequest.nåværendeSpørsmålId.tilUUID("nåværrendeSpørsmålId")

            if (spørreundersøkelse.spørsmålOgSvaralternativer.none { it.id == nåværrendeSpørsmålId }) {
                call.application.log.warn("Ukjent spørsmålId: $nåværrendeSpørsmålId")
                call.respond(
                    HttpStatusCode.BadRequest
                )
            }
            spørreundersøkelse.spørsmålOgSvaralternativer.indexOfFirst { it.id == nåværrendeSpørsmålId }
        }

        val indeksTilSisteSpørsmål = spørreundersøkelse.spørsmålOgSvaralternativer.size - 1
        val nesteSpørsmålIndeks = indeksTilNåværendeSpørsmålId?.plus(1) ?: 0
        val forrigeSpørsmålIndeks = indeksTilNåværendeSpørsmålId?.minus(1) ?: -2

        val temastatus = spørreundersøkelseService.hentTemastatus(
            spørreundersøkelseId,
            if (nesteSpørsmålIndeks > indeksTilSisteSpørsmål) {
                spørreundersøkelse.spørsmålOgSvaralternativer[indeksTilSisteSpørsmål].tema
            } else {
                spørreundersøkelse.spørsmålOgSvaralternativer[nesteSpørsmålIndeks].tema
            }
        )

        var åpnetFremTilIndeks = temastatus?.spørsmålindeks ?: -1 // ikke åpnet når vi ikke har katgoristatus

        // -- TODO: restrukturer temaer og spøsmålsgrupper ------- shun charlieee
        if (temastatus?.tema == Tema.REDUSERE_SYKEFRAVÆR)
            åpnetFremTilIndeks += 2
        // -- TODO: restrukturer temaer og spøsmålsgrupper ------- shun charlieee

        val nåværendeTema = spørreundersøkelse.spørsmålOgSvaralternativer[indeksTilNåværendeSpørsmålId ?: 0].tema
        val nesteSteg =
            if (nesteSpørsmålIndeks > indeksTilSisteSpørsmål)
                NesteSpørsmålDTO.StegStatus.FERDIG
            else if (temastatus?.tema != nåværendeTema)
                NesteSpørsmålDTO.StegStatus.NYTT_TEMA
            else
                NesteSpørsmålDTO.StegStatus.NYTT_SPØRSMÅL

        call.respond(
            HttpStatusCode.OK,
            NesteSpørsmålDTO(
                nåværendeSpørsmålIndeks = indeksTilNåværendeSpørsmålId ?: -1,
                sisteSpørsmålIndeks = indeksTilSisteSpørsmål,
                hvaErNesteSteg = nesteSteg,
                erNesteÅpnetAvVert = nesteSpørsmålIndeks <= åpnetFremTilIndeks,
                nesteSpørsmålId = if (nesteSpørsmålIndeks <= indeksTilSisteSpørsmål) spørreundersøkelse.spørsmålOgSvaralternativer[nesteSpørsmålIndeks].id.toString() else null,
                forrigeSpørsmålId = if (forrigeSpørsmålIndeks >= 0) spørreundersøkelse.spørsmålOgSvaralternativer[forrigeSpørsmålIndeks].id.toString() else null,
            )
        )
    }

    post(SVAR_PATH) {
        val svarRequest = call.receive(SvarRequest::class)

        val spørreundersøkelseId = svarRequest.spørreundersøkelseId.tilUUID("spørreundersøkelseId")
        val sesjonsId = svarRequest.sesjonsId.tilUUID("sesjonsId")

        validerSesjonsId(
            spørreundersøkelseService = spørreundersøkelseService,
            sesjonsId = sesjonsId,
            spørreundersøkelseId = spørreundersøkelseId
        )

        val spørsmålId = svarRequest.spørsmålId.tilUUID("spørsmålId")
        val svarId = svarRequest.svarId.tilUUID("svarId")
        val spørreundersøkelse = spørreundersøkelseService.hentePågåendeSpørreundersøkelse(spørreundersøkelseId)

        val spørsmål = spørreundersøkelse.spørsmålOgSvaralternativer.firstOrNull { it.id == spørsmålId }
            ?: throw Feil(feilmelding = "Ukjent spørsmål ($spørsmålId)", feilkode = HttpStatusCode.Forbidden)

        if (spørsmål.svaralternativer.none { it.svarId == svarId })
            throw Feil(feilmelding = "Ukjent svar ($svarId)", feilkode = HttpStatusCode.Forbidden)

        call.application.log.info("Har fått inn svar $svarId")
        spørreundersøkelseSvarProdusent.sendSvar(
            SpørreundersøkelseSvarDTO(
                spørreundersøkelseId = spørreundersøkelse.spørreundersøkelseId.toString(),
                sesjonId = sesjonsId.toString(),
                spørsmålId = spørsmålId.toString(),
                svarId = svarId.toString()
            )
        )

        call.respond(
            HttpStatusCode.OK,
        )
    }

    post(VERT_TEMA_PATH) {
        val vertshandlingRequest = call.receive(VertshandlingRequest::class)

        val spørreundersøkelseId = vertshandlingRequest.spørreundersøkelseId.tilUUID("spørreundersøkelseId")
            ?: throw Feil(
                "SpørreundersøkelseId skal aldri kunne være null", feilkode = HttpStatusCode.InternalServerError
            )
        val vertId = vertshandlingRequest.vertId.tilUUID("vertId")
        validerVertId(
            spørreundersøkelseService = spørreundersøkelseService,
            spørreundersøkelseId = spørreundersøkelseId,
            vertId = vertId
        )
        val temastatus = spørreundersøkelseService.hentTemastatus(
            spørreundersøkelseId,
            vertshandlingRequest.tema
        ) ?: throw Feil(
            "Finner ikke temastatus på undersøkelse $spørreundersøkelseId",
            feilkode = HttpStatusCode.InternalServerError
        )

        call.respond(HttpStatusCode.OK, temastatus)
    }

    post(VERT_ANTALL_DELTAKERE_PATH) {
        val vertshandlingRequest = call.receive(VertshandlingRequest::class)

        val spørreundersøkelseId = vertshandlingRequest.spørreundersøkelseId.tilUUID("spørreundersøkelseId")
        val vertId = vertshandlingRequest.vertId.tilUUID("vertId")

        validerVertId(
            spørreundersøkelseService = spørreundersøkelseService,
            spørreundersøkelseId = spørreundersøkelseId,
            vertId = vertId
        )

        val antallDeltakere = spørreundersøkelseService.hentAntallDeltakere(spørreundersøkelseId)

        val spørreundersøkelse = spørreundersøkelseService.hentePågåendeSpørreundersøkelse(spørreundersøkelseId)

        val antallSvar =
            spørreundersøkelse.spørsmålOgSvaralternativer.map { spørsmål ->
                AntallSvarDTO(
                    spørsmålId = spørsmål.id.toString(),
                    antall = spørreundersøkelseService.hentAntallSvar(
                        spørreundersøkelse.spørreundersøkelseId,
                        spørsmål.id
                    )
                )
            }

        call.respond(
            HttpStatusCode.OK,
            AntallDeltakereDTO(
                spørreundersøkelseId = spørreundersøkelseId.toString(),
                antallDeltakere = antallDeltakere,
                antallSvar = antallSvar,
            )
        )
    }

    post("$VERT_ANTALL_SVAR_PATH/{spørsmålId}") {
        val vertshandlingRequest = call.receive(VertshandlingRequest::class)
        val spørsmålId =
            call.spørsmålId?.tilUUID("spørsmålId") ?: return@post call.respond(HttpStatusCode.BadRequest)
        val spørreundersøkelseId = vertshandlingRequest.spørreundersøkelseId.tilUUID("spørreundersøkelseId")
        val vertId = vertshandlingRequest.vertId.tilUUID("vertId")

        validerVertId(
            spørreundersøkelseService = spørreundersøkelseService,
            spørreundersøkelseId = spørreundersøkelseId,
            vertId = vertId
        )

        call.respond(
            HttpStatusCode.OK,
            AntallSvarPerSpørsmålDTO(
                antallDeltakere = spørreundersøkelseService.hentAntallDeltakere(spørreundersøkelseId),
                antallSvar = spørreundersøkelseService.hentAntallSvar(spørreundersøkelseId, spørsmålId)
            )
        )
    }

    post(VERT_INKREMENTER_SPØRSMÅL_PATH) {
        val vertshandlingRequest = call.receive(VertshandlingRequest::class)

        val spørreundersøkelseId = vertshandlingRequest.spørreundersøkelseId.tilUUID("spørreundersøkelseId")
        val vertId = vertshandlingRequest.vertId.tilUUID("vertId")

        validerVertId(
            spørreundersøkelseService = spørreundersøkelseService,
            spørreundersøkelseId = spørreundersøkelseId,
            vertId = vertId
        )

        val temastatus = spørreundersøkelseService.hentTemastatus(
            spørreundersøkelseId,
            vertshandlingRequest.tema
        ) ?: throw Feil(
            "Finner ikke temastatus på undersøkelse $spørreundersøkelseId",
            feilkode = HttpStatusCode.InternalServerError
        )

        if (!(temastatus.status == IKKE_PÅBEGYNT || temastatus.status == PÅBEGYNT)) {
            throw Feil(feilmelding = "Temaet er i en ugyldig status", feilkode = HttpStatusCode.Conflict)
        }

        val nyIndeks = if (temastatus.spørsmålindeks == null) 0 else temastatus.spørsmålindeks + 1

        val inkrementert = temastatus.copy(
            spørsmålindeks = nyIndeks,
            status = PÅBEGYNT,
        )
        spørreundersøkelseService.lagreTemastatus(spørreundersøkelseId, inkrementert)

        call.respond(HttpStatusCode.OK, inkrementert)
    }


    post(VERT_START_TEMA_PATH) {
        val startTemaRequest = call.receive(StartTemaRequest::class)

        val spørreundersøkelseId = startTemaRequest.spørreundersøkelseId.tilUUID("spørreundersøkelseId")
        val vertId = startTemaRequest.vertId.tilUUID("vertId")
        val tema = startTemaRequest.tema

        validerVertId(
            spørreundersøkelseService = spørreundersøkelseService,
            spørreundersøkelseId = spørreundersøkelseId,
            vertId = vertId
        )

        val temastatus = spørreundersøkelseService.hentTemastatus(
            spørreundersøkelseId,
            tema
        ) ?: throw Feil(
            "Temastatus på undersøkelse $spørreundersøkelseId finnes ikke",
            feilkode = HttpStatusCode.InternalServerError
        )

        spørreundersøkelseService.lagreTemastatus(
            spørreundersøkelseId,
            temastatus.copy(status = IKKE_PÅBEGYNT)
        )

        call.respond(HttpStatusCode.OK)
    }

    //TODO: @Deprecated("Denne må erstattes med /{spørsmålId})")
    post(VERT_SPØRSMÅL_OG_SVAR_PATH) {
        val vertshandlingRequest = call.receive(VertshandlingRequest::class)

        val spørreundersøkelseId = vertshandlingRequest.spørreundersøkelseId.tilUUID("spørreundersøkelseId")
        val vertId = vertshandlingRequest.vertId.tilUUID("vertId")
        val spørreundersøkelse = spørreundersøkelseService.hentePågåendeSpørreundersøkelse(spørreundersøkelseId)

        if (spørreundersøkelse.vertId != vertId)
            throw Feil(
                feilmelding = "Ugyldig vertId: $vertId",
                feilkode = HttpStatusCode.Forbidden
            )

        call.respond(
            HttpStatusCode.OK,
            SpørsmålOgSvaralternativerDTO.toDto(spørreundersøkelse.spørsmålOgSvaralternativer)
        )
    }
}

private fun validerVertId(
    spørreundersøkelseService: SpørreundersøkelseService,
    spørreundersøkelseId: UUID,
    vertId: UUID,
) {
    if (spørreundersøkelseService.hentePågåendeSpørreundersøkelse(spørreundersøkelseId).vertId != vertId)
        throw Feil(
            feilmelding = "Ugyldig vertId: $vertId",
            feilkode = HttpStatusCode.Forbidden
        )
}

private fun validerSesjonsId(
    spørreundersøkelseService: SpørreundersøkelseService,
    sesjonsId: UUID,
    spørreundersøkelseId: UUID,
) {
    if (spørreundersøkelseService.henteSpørreundersøkelseIdFraSesjon(sesjonsId) != spørreundersøkelseId)
        throw Feil(feilmelding = "Ugyldig sesjonsId", feilkode = HttpStatusCode.Forbidden)
}

private fun String.tilUUID(hvaErJeg: String) = try {
    UUID.fromString(this)
} catch (e: IllegalArgumentException) {
    throw Feil("Ugyldig formatert UUID $hvaErJeg: $this", e, HttpStatusCode.BadRequest)
}

private val ApplicationCall.spørsmålId
    get() = parameters["spørsmålId"]