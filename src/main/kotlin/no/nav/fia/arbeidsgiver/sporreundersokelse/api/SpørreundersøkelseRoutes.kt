package no.nav.fia.arbeidsgiver.sporreundersokelse.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import no.nav.fia.arbeidsgiver.api.Feil
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.KategoristatusDTO.Status.IKKE_PÅBEGYNT
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.KategoristatusDTO.Status.PÅBEGYNT
import no.nav.fia.arbeidsgiver.persistence.RedisService
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.AntallDeltakereDTO
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.AntallSvarDTO
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.BliMedDTO
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.BliMedRequest
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.DeltakerhandlingRequest
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.NesteSpørsmålDTO
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.NesteSpørsmålRequest
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.SpørsmålOgSvaralternativerDTO
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.StarteKategoriRequest
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.SvarRequest
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.VertshandlingRequest
import no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.SpørreundersøkelseSvarDTO
import no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.SpørreundersøkelseSvarProdusent
import java.util.*
import kotlin.IllegalArgumentException

const val SPØRREUNDERSØKELSE_PATH = "/fia-arbeidsgiver/sporreundersokelse"
const val BLI_MED_PATH = "$SPØRREUNDERSØKELSE_PATH/bli-med"
const val SPØRSMÅL_OG_SVAR_PATH = "$SPØRREUNDERSØKELSE_PATH/sporsmal-og-svar"
const val NESTE_SPØRSMÅL_PATH = "$SPØRREUNDERSØKELSE_PATH/neste-sporsmal"
const val SVAR_PATH = "$SPØRREUNDERSØKELSE_PATH/svar"
const val KATEGORISTATUS_PATH = "$SPØRREUNDERSØKELSE_PATH/kategoristatus"

const val VERT_ANTALL_DELTAKERE_PATH = "$SPØRREUNDERSØKELSE_PATH/vert/antall-deltakere"
const val VERT_START_KATEGORI_PATH = "$SPØRREUNDERSØKELSE_PATH/vert/start-kategori"
const val VERT_INKREMENTER_SPØRSMÅL_PATH = "$SPØRREUNDERSØKELSE_PATH/vert/inkrementer-sporsmal"
const val VERT_SPØRSMÅL_OG_SVAR_PATH = "$SPØRREUNDERSØKELSE_PATH/vert/sporsmal-og-svar"
const val VERT_KATEGORISTATUS_PATH = "$SPØRREUNDERSØKELSE_PATH/vert/kategoristatus"

fun Route.spørreundersøkelse(redisService: RedisService) {
    val spørreundersøkelseSvarProdusent = SpørreundersøkelseSvarProdusent()
    post(BLI_MED_PATH) {
        val bliMedRequest = call.receive(BliMedRequest::class)

        val spørreundersøkelseId = bliMedRequest.spørreundersøkelseId.tilUUID("spørreundersøkelseId")

        val spørreundersøkelse = redisService.hentePågåendeSpørreundersøkelse(spørreundersøkelseId)

        val sesjonsId = UUID.randomUUID()
        redisService.lagreSesjon(sesjonsId, spørreundersøkelse.spørreundersøkelseId)

        val antallDeltakere = redisService.hentAntallDeltakere(spørreundersøkelse.spørreundersøkelseId)
        redisService.lagreAntallDeltakere(spørreundersøkelse.spørreundersøkelseId, (antallDeltakere + 1))

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
            redisService = redisService,
            sesjonsId = sesjonsId,
            spørreundersøkelseId = spørreundersøkelseId
        )

        val spørreundersøkelse = redisService.hentePågåendeSpørreundersøkelse(spørreundersøkelseId)


        call.respond(
            HttpStatusCode.OK,
            SpørsmålOgSvaralternativerDTO.toDto(spørreundersøkelse.spørsmålOgSvaralternativer)
        )
    }

    post(KATEGORISTATUS_PATH) {
        val request = call.receive(DeltakerhandlingRequest::class)

        val spørreundersøkelseId = request.spørreundersøkelseId.tilUUID("spørreundersøkelseId")
            ?: throw Feil(
                "SpørreundersøkelseId skal aldri kunne være null", feilkode = HttpStatusCode.InternalServerError
            )
        val sesjonsId = request.sesjonsId.tilUUID("sesjonsId")

        validerSesjonsId(
            redisService = redisService,
            sesjonsId = sesjonsId,
            spørreundersøkelseId = spørreundersøkelseId
        )

        val kategoristatus = redisService.hentKategoristatus(
            spørreundersøkelseId,
            request.kategori
        ) ?: throw Feil(
            "Finner ikke kategoristatus på undersøkelse $spørreundersøkelseId",
            feilkode = HttpStatusCode.InternalServerError
        )

        call.respond(HttpStatusCode.OK, kategoristatus)
    }


    post("$SPØRSMÅL_OG_SVAR_PATH/{spørsmålId}") {
        val deltakerhandlingRequest = call.receive(DeltakerhandlingRequest::class)
        val spørsmålId =
            call.spørsmålId?.tilUUID("spørsmålId") ?: return@post call.respond(HttpStatusCode.BadRequest)
        val spørreundersøkelseId = deltakerhandlingRequest.spørreundersøkelseId.tilUUID("spørreundersøkelseId")
        val sesjonsId = deltakerhandlingRequest.sesjonsId.tilUUID("sesjonsId")

        validerSesjonsId(
            redisService = redisService,
            sesjonsId = sesjonsId,
            spørreundersøkelseId = spørreundersøkelseId
        )

        val spørreundersøkelse = redisService.hentePågåendeSpørreundersøkelse(spørreundersøkelseId)
        val indeksTilSpørsmålId = spørreundersøkelse.spørsmålOgSvaralternativer.indexOfFirst { it.id == spørsmålId }
        val spørsmålOgSvaralternativer = spørreundersøkelse.spørsmålOgSvaralternativer.firstOrNull { it.id == spørsmålId }

        if (indeksTilSpørsmålId == -1 || spørsmålOgSvaralternativer == null) {
            call.application.log.warn("Spørsmål med id $spørsmålId ble ikke funnet")
            call.respond(HttpStatusCode.NotFound)
        } else {
            val indeksTilSisteSpørsmål = spørreundersøkelse.spørsmålOgSvaralternativer.size - 1
            call.respond(
                HttpStatusCode.OK,
                spørsmålOgSvaralternativer.toFrontendDto(indeksTilSpørsmålId, indeksTilSisteSpørsmål)
            )
        }
    }

    post(NESTE_SPØRSMÅL_PATH) {
        val nesteSpørsmålRequest = call.receive(NesteSpørsmålRequest::class)

        val spørreundersøkelseId =
            nesteSpørsmålRequest.spørreundersøkelseId.tilUUID("spørreundersøkelseId")
        val sesjonsId = nesteSpørsmålRequest.sesjonsId.tilUUID("sesjonsId")

        validerSesjonsId(
            redisService = redisService,
            sesjonsId = sesjonsId,
            spørreundersøkelseId = spørreundersøkelseId
        )

        val spørreundersøkelse = redisService.hentePågåendeSpørreundersøkelse(spørreundersøkelseId)

        val indeksTilNåværrendeSpørsmålId = if (nesteSpørsmålRequest.nåværrendeSpørsmålId.uppercase() == "START") {
            -1
        } else {
            val nåværrendeSpørsmålId = nesteSpørsmålRequest.nåværrendeSpørsmålId.tilUUID("nåværrendeSpørsmålId")

            if (spørreundersøkelse.spørsmålOgSvaralternativer.none { it.id == nåværrendeSpørsmålId }) {
                call.application.log.warn("Ukjent spørsmålId: $nåværrendeSpørsmålId")
                call.respond(
                    HttpStatusCode.BadRequest
                )
            }
            spørreundersøkelse.spørsmålOgSvaralternativer.indexOfFirst { it.id == nåværrendeSpørsmålId }
        }

        val indeksTilSisteSpørsmål = spørreundersøkelse.spørsmålOgSvaralternativer.size - 1
        val nesteSpørsmålIndeks = indeksTilNåværrendeSpørsmålId + 1
        val forrigeSpørsmålIndeks = indeksTilNåværrendeSpørsmålId - 1

        val kategoristatus = redisService.hentKategoristatus(
            spørreundersøkelseId,
            if(nesteSpørsmålIndeks > indeksTilSisteSpørsmål) {
                spørreundersøkelse.spørsmålOgSvaralternativer[indeksTilSisteSpørsmål].kategori
            } else {
                spørreundersøkelse.spørsmålOgSvaralternativer[nesteSpørsmålIndeks].kategori
            }
        )
        val åpnetFremTilIndeks = kategoristatus?.spørsmålindeks ?: -1 // ikke åpnet når vi ikke har katgoristatus

        call.respond(
            HttpStatusCode.OK,
            NesteSpørsmålDTO(
                nåværendeSpørsmålIndeks = indeksTilNåværrendeSpørsmålId,
                sisteSpørsmålIndeks = indeksTilSisteSpørsmål,
                hvaErNesteSteg = if (nesteSpørsmålIndeks > indeksTilSisteSpørsmål) NesteSpørsmålDTO.StegStatus.FERDIG else NesteSpørsmålDTO.StegStatus.NYTT_SPØRSMÅL,
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
            redisService = redisService,
            sesjonsId = sesjonsId,
            spørreundersøkelseId = spørreundersøkelseId
        )

        val spørsmålId = svarRequest.spørsmålId.tilUUID("spørsmålId")
        val svarId = svarRequest.svarId.tilUUID("svarId")
        val spørreundersøkelse = redisService.hentePågåendeSpørreundersøkelse(spørreundersøkelseId)

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



    post(VERT_KATEGORISTATUS_PATH) {
        val vertshandlingRequest = call.receive(VertshandlingRequest::class)

        val spørreundersøkelseId = vertshandlingRequest.spørreundersøkelseId.tilUUID("spørreundersøkelseId")
            ?: throw Feil(
                "SpørreundersøkelseId skal aldri kunne være null", feilkode = HttpStatusCode.InternalServerError
            )
        val vertId = vertshandlingRequest.vertId.tilUUID("vertId")
        validerVertId(
            redisService = redisService,
            spørreundersøkelseId = spørreundersøkelseId,
            vertId = vertId
        )
        val kategoristatus = redisService.hentKategoristatus(
            spørreundersøkelseId,
            vertshandlingRequest.kategori
        ) ?: throw Feil(
            "Finner ikke kategoristatus på undersøkelse $spørreundersøkelseId",
            feilkode = HttpStatusCode.InternalServerError
        )

        call.respond(HttpStatusCode.OK, kategoristatus)
    }

    post(VERT_ANTALL_DELTAKERE_PATH) {
        val vertshandlingRequest = call.receive(VertshandlingRequest::class)

        val spørreundersøkelseId = vertshandlingRequest.spørreundersøkelseId.tilUUID("spørreundersøkelseId")
        val vertId = vertshandlingRequest.vertId.tilUUID("vertId")

        validerVertId(
            redisService = redisService,
            spørreundersøkelseId = spørreundersøkelseId,
            vertId = vertId
        )

        val antallDeltakere = redisService.hentAntallDeltakere(spørreundersøkelseId)

        val spørreundersøkelse = redisService.hentePågåendeSpørreundersøkelse(spørreundersøkelseId)

        val antallSvar =
            spørreundersøkelse.spørsmålOgSvaralternativer.map { spørsmål ->
                AntallSvarDTO(
                    spørsmålId = spørsmål.id.toString(),
                    antall = spørsmål.antallSvar
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

    post(VERT_INKREMENTER_SPØRSMÅL_PATH) {
        val vertshandlingRequest = call.receive(VertshandlingRequest::class)

        val spørreundersøkelseId = vertshandlingRequest.spørreundersøkelseId.tilUUID("spørreundersøkelseId")
        val vertId = vertshandlingRequest.vertId.tilUUID("vertId")

        validerVertId(redisService = redisService, spørreundersøkelseId = spørreundersøkelseId, vertId = vertId)

        // Dette støtter foreløpig bare én kategori.
        val kategoristatus = redisService.hentKategoristatus(
            spørreundersøkelseId,
            vertshandlingRequest.kategori
        ) ?: throw Feil(
            "Finner ikke kategoristatus på undersøkelse $spørreundersøkelseId",
            feilkode = HttpStatusCode.InternalServerError
        )

        if (!(kategoristatus.status == IKKE_PÅBEGYNT || kategoristatus.status == PÅBEGYNT)) {
            throw Feil(feilmelding = "Kategorien er i en ugyldig status", feilkode = HttpStatusCode.Conflict)
        }

        val nyIndeks = if (kategoristatus.spørsmålindeks == null) 0 else kategoristatus.spørsmålindeks + 1

        val inkrementert = kategoristatus.copy(
            spørsmålindeks = nyIndeks,
            status = PÅBEGYNT,
        )
        redisService.lagreKategoristatus(spørreundersøkelseId, inkrementert)

        call.respond(HttpStatusCode.OK, inkrementert)
    }


    post(VERT_START_KATEGORI_PATH) {
        val starteKategoriRequest = call.receive(StarteKategoriRequest::class)

        val spørreundersøkelseId = starteKategoriRequest.spørreundersøkelseId.tilUUID("spørreundersøkelseId")
        val vertId = starteKategoriRequest.vertId.tilUUID("vertId")
        val kategori = starteKategoriRequest.kategori

        validerVertId(
            redisService = redisService,
            spørreundersøkelseId = spørreundersøkelseId,
            vertId = vertId
        )

        val kategoristatus = redisService.hentKategoristatus(
            spørreundersøkelseId,
            kategori
        ) ?: throw Feil(
            "Kategoristatus på undersøkelse $spørreundersøkelseId finnes ikke",
            feilkode = HttpStatusCode.InternalServerError
        )

        redisService.lagreKategoristatus(
            spørreundersøkelseId,
            kategoristatus.copy(status = IKKE_PÅBEGYNT)
        )

        call.respond(HttpStatusCode.OK)
    }

    post(VERT_SPØRSMÅL_OG_SVAR_PATH) {
        val vertshandlingRequest = call.receive(VertshandlingRequest::class)

        val spørreundersøkelseId = vertshandlingRequest.spørreundersøkelseId.tilUUID("spørreundersøkelseId")
        val vertId = vertshandlingRequest.vertId.tilUUID("vertId")
        val spørreundersøkelse = redisService.hentePågåendeSpørreundersøkelse(spørreundersøkelseId)

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
    redisService: RedisService,
    spørreundersøkelseId: UUID,
    vertId: UUID?
) {
    if (redisService.hentePågåendeSpørreundersøkelse(spørreundersøkelseId).vertId != vertId)
        throw Feil(
            feilmelding = "Ugyldig vertId: $vertId",
            feilkode = HttpStatusCode.Forbidden
        )
}

private fun validerSesjonsId(
    redisService: RedisService,
    sesjonsId: UUID,
    spørreundersøkelseId: UUID
) {
    if (redisService.henteSpørreundersøkelseIdFraSesjon(sesjonsId) != spørreundersøkelseId)
        throw Feil(feilmelding = "Ugyldig sesjonsId", feilkode = HttpStatusCode.Forbidden)
}

private fun String.tilUUID(hvaErJeg: String) = try {
    UUID.fromString(this)
} catch (e: IllegalArgumentException) {
    throw Feil("Ugyldig formatert UUID $hvaErJeg: $this", e, HttpStatusCode.BadRequest)
}

private val ApplicationCall.spørsmålId
    get() = parameters["spørsmålId"]