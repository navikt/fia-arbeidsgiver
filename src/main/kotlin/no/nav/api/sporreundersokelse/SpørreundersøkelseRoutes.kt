package no.nav.api.sporreundersokelse

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.application.log
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import no.nav.api.Feil
import no.nav.kafka.SpørreundersøkelseSvar
import no.nav.kafka.SpørreundersøkelseSvarProdusent
import no.nav.persistence.RedisService
import java.lang.IllegalArgumentException
import java.util.*

const val SPØRREUNDERSØKELSE_PATH = "/fia-arbeidsgiver/sporreundersokelse"
const val BLI_MED_PATH = "${SPØRREUNDERSØKELSE_PATH}/bli-med"
const val SPØRSMÅL_OG_SVAR_PATH = "${SPØRREUNDERSØKELSE_PATH}/sporsmal-og-svar"
const val SVAR_PATH = "${SPØRREUNDERSØKELSE_PATH}/svar"
const val GJELDENDE_SPØRSMÅL_PATH = "${SPØRREUNDERSØKELSE_PATH}/gjeldende-sporsmal"

const val VERT_ANTALL_DELTAKERE_PATH = "${SPØRREUNDERSØKELSE_PATH}/vert/antall-deltakere"
const val VERT_GJELDENDE_SPØRSMÅL_PATH = "${SPØRREUNDERSØKELSE_PATH}/vert/gjeldende-sporsmal"
const val VERT_NESTE_SPØRSMÅL_PATH = "${SPØRREUNDERSØKELSE_PATH}/vert/neste-sporsmal"
const val VERT_SPØRSMÅL_OG_SVAR_PATH = "${SPØRREUNDERSØKELSE_PATH}/vert/sporsmal-og-svar"

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
        val spørsmålOgSvarRequest = call.receive(SpørsmålOgSvarRequest::class)

        val id = spørsmålOgSvarRequest.spørreundersøkelseId.tilUUID("spørreundersøkelseId")

        val sesjonsId = spørsmålOgSvarRequest.sesjonsId.tilUUID("sesjonsId")
        if (redisService.henteSpørreundersøkelseIdFraSesjon(sesjonsId) != id)
            throw Feil(feilmelding = "Ugyldig sesjonsId", feilkode = HttpStatusCode.Forbidden)

        val spørreundersøkelse = redisService.hentePågåendeSpørreundersøkelse(id)


        call.respond(
            HttpStatusCode.OK,
            SpørsmålOgSvaralternativerDTO.toDto(spørreundersøkelse.spørsmålOgSvaralternativer)
        )
    }

    post(GJELDENDE_SPØRSMÅL_PATH) {
        val statusRequest = call.receive(StatusRequest::class)

        val spørreundersøkelseId = statusRequest.spørreundersøkelseId.tilUUID("spørreundersøkelseId")
        val sesjonsId = statusRequest.sesjonsId.tilUUID("sesjonsId")
        if (redisService.henteSpørreundersøkelseIdFraSesjon(sesjonsId) != spørreundersøkelseId)
            throw Feil(feilmelding = "Ugyldig sesjonsId", feilkode = HttpStatusCode.Forbidden)

        val spørsmålindeks = redisService.hentSpørsmålindeks(spørreundersøkelseId)

        call.respond(
            HttpStatusCode.OK,
            SpørsmålindeksDTO(
                spørreundersøkelseId = spørreundersøkelseId.toString(),
                indeks = spørsmålindeks
            )
        )
    }

    post(SVAR_PATH) {
        val svarRequest = call.receive(SvarRequest::class)

        val spørreundersøkelseId = svarRequest.spørreundersøkelseId.tilUUID("spørreundersøkelseId")

        val sesjonsId = svarRequest.sesjonsId.tilUUID("sesjonsId")
        if (redisService.henteSpørreundersøkelseIdFraSesjon(sesjonsId) != spørreundersøkelseId)
            throw Feil(feilmelding = "Ugyldig sesjonsId", feilkode = HttpStatusCode.Forbidden)

        val spørsmålId = svarRequest.spørsmålId.tilUUID("spørsmålId")
        val svarId = svarRequest.svarId.tilUUID("svarId")
        val spørreundersøkelse = redisService.hentePågåendeSpørreundersøkelse(spørreundersøkelseId)

        val spørsmål = spørreundersøkelse.spørsmålOgSvaralternativer.firstOrNull { it.id == spørsmålId }
            ?: throw Feil(feilmelding = "Ukjent spørsmål ($spørsmålId)", feilkode = HttpStatusCode.Forbidden)

        if (spørsmål.svaralternativer.none { it.svarId == svarId })
            throw Feil(feilmelding = "Ukjent svar ($svarId)", feilkode = HttpStatusCode.Forbidden)

        call.application.log.info("Har fått inn svar $svarId")
        spørreundersøkelseSvarProdusent.sendSvar(
            SpørreundersøkelseSvar(
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

    post(VERT_ANTALL_DELTAKERE_PATH) {
        val vertshandlingRequest = call.receive(VertshandlingRequest::class)

        val spørreundersøkelseId = vertshandlingRequest.spørreundersøkelseId.tilUUID("spørreundersøkelseId")
        val vertId = vertshandlingRequest.vertId.tilUUID("vertId")

        val spørreundersøkelse = redisService.hentePågåendeSpørreundersøkelse(spørreundersøkelseId)

        if (spørreundersøkelse.vertId != vertId)
            throw Feil(
                feilmelding = "Ugyldig vertId: $vertId",
                feilkode = HttpStatusCode.Forbidden
            )

        val antallDeltakere = redisService.hentAntallDeltakere(spørreundersøkelseId)

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

    post(VERT_NESTE_SPØRSMÅL_PATH) {
        val vertshandlingRequest = call.receive(VertshandlingRequest::class)

        val spørreundersøkelseId = vertshandlingRequest.spørreundersøkelseId.tilUUID("spørreundersøkelseId")
        val vertId = vertshandlingRequest.vertId.tilUUID("vertId")

        if (redisService.hentePågåendeSpørreundersøkelse(spørreundersøkelseId).vertId != vertId)
            throw Feil(
                feilmelding = "Ugyldig vertId: $vertId",
                feilkode = HttpStatusCode.Forbidden
            )

        val nesteSpørsmålindeks = redisService.hentSpørsmålindeks(spørreundersøkelseId) + 1
        redisService.lagreSpørsmålindeks(spørreundersøkelseId, nesteSpørsmålindeks)

        call.respond(
            HttpStatusCode.OK,
            SpørsmålindeksDTO(
                spørreundersøkelseId = spørreundersøkelseId.toString(),
                indeks = nesteSpørsmålindeks
            )
        )
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

    post(VERT_GJELDENDE_SPØRSMÅL_PATH) {
        val vertshandlingRequest = call.receive(VertshandlingRequest::class)

        val spørreundersøkelseId = vertshandlingRequest.spørreundersøkelseId.tilUUID("spørreundersøkelseId")
        val vertId = vertshandlingRequest.vertId.tilUUID("vertId")
        val spørreundersøkelse = redisService.hentePågåendeSpørreundersøkelse(spørreundersøkelseId)

        if (spørreundersøkelse.vertId != vertId)
            throw Feil(
                feilmelding = "Ugyldig vertId: $vertId",
                feilkode = HttpStatusCode.Forbidden
            )

        val spørsmålindeks = redisService.hentSpørsmålindeks(spørreundersøkelseId)

        call.respond(
            HttpStatusCode.OK,
            SpørsmålindeksDTO(
                spørreundersøkelseId = spørreundersøkelseId.toString(),
                indeks = spørsmålindeks
            )
        )
    }
}

private fun String.tilUUID(hvaErJeg: String) = try {
    UUID.fromString(this)
} catch (e: IllegalArgumentException) {
    throw Feil("Ugyldig formatert UUID $hvaErJeg: $this", e, HttpStatusCode.BadRequest)
}
