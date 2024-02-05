package no.nav.api.sporreundersokelse

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.application.log
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
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
const val ANTALL_DELTAKERE_PATH = "${SPØRREUNDERSØKELSE_PATH}/antall-deltakere"
const val NESTE_SPØRSMÅL_PATH = "${SPØRREUNDERSØKELSE_PATH}/neste-sporsmal"
const val GJELDENDE_SPØRSMÅL_PATH = "${SPØRREUNDERSØKELSE_PATH}/gjeldende-sporsmal"

fun Route.spørreundersøkelse(redisService: RedisService) {
    val spørreundersøkelseSvarProdusent = SpørreundersøkelseSvarProdusent()
    rateLimit(RateLimitName("sporreundersokelse-bli-med")){
        post(BLI_MED_PATH) {
            val bliMedRequest = call.receive(BliMedRequest::class)

            val spørreundersøkelseId = bliMedRequest.spørreundersøkelseId.tilUUID("spørreundersøkelseId")

            val spørreundersøkelse = redisService.hentePågåendeSpørreundersøkelse(spørreundersøkelseId)

            val sesjonsId = UUID.randomUUID()
            redisService.lagreSesjon(sesjonsId, spørreundersøkelse.spørreundersøkelseId)

            val antallDeltakere = redisService.hentAntallDeltakere(spørreundersøkelse.spørreundersøkelseId)
            redisService.lagreAntallDeltakere(spørreundersøkelse.spørreundersøkelseId, (antallDeltakere+1))

            call.respond(HttpStatusCode.OK, BliMedDTO(
                spørreundersøkelseId = spørreundersøkelse.spørreundersøkelseId.toString(),
                sesjonsId = sesjonsId.toString()
            ))
        }
    }

    rateLimit(RateLimitName("sporreundersokelse")) {
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

        post(ANTALL_DELTAKERE_PATH) {
            val vertshandlingRequest = call.receive(VertshandlingRequest::class)

            val spørreundersøkelseId = vertshandlingRequest.spørreundersøkelseId.tilUUID("spørreundersøkelseId")

            val antallDeltakere = redisService.hentAntallDeltakere(spørreundersøkelseId)

            call.respond(
                HttpStatusCode.OK,
                AntallDeltakereDTO(
                    spørreundersøkelseId = spørreundersøkelseId.toString(),
                    antallDeltakere = antallDeltakere
                )
            )
        }
        post(NESTE_SPØRSMÅL_PATH) {
            val vertshandlingRequest = call.receive(VertshandlingRequest::class)

            val spørreundersøkelseId = vertshandlingRequest.spørreundersøkelseId.tilUUID("spørreundersøkelseId")

            val nesteSpørsmålindeks = redisService.hentSpørsmålindeks(spørreundersøkelseId)+1
            redisService.lagreSpørsmålindeks(spørreundersøkelseId, nesteSpørsmålindeks)

            call.respond(
                HttpStatusCode.OK,
                SpørsmålindeksDTO(
                    spørreundersøkelseId = spørreundersøkelseId.toString(),
                    indeks = nesteSpørsmålindeks
                )
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
    }
}

private fun String.tilUUID(hvaErJeg: String) = try{
    UUID.fromString(this)
} catch (e: IllegalArgumentException) {
    throw Feil("Ugyldig formatert UUID $hvaErJeg: $this", e, HttpStatusCode.BadRequest)
}
