package no.nav.api.kartlegging

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.application.log
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import no.nav.api.Feil
import no.nav.kafka.KartleggingSvar
import no.nav.kafka.KartleggingSvarProdusent
import no.nav.persistence.RedisService
import java.lang.IllegalArgumentException
import java.util.*

const val KARTLEGGING_PATH = "/fia-arbeidsgiver/kartlegging"
const val BLI_MED_PATH = "${KARTLEGGING_PATH}/bli-med"
const val SPØRSMÅL_OG_SVAR_PATH = "${KARTLEGGING_PATH}/sporsmal-og-svar"
const val SVAR_PATH = "${KARTLEGGING_PATH}/svar"

fun Route.kartlegging(redisService: RedisService) {
    val kartleggingSvarProdusent = KartleggingSvarProdusent()
    rateLimit(RateLimitName("kartlegging-bli-med")){
        post(BLI_MED_PATH) {
            val bliMedRequest = call.receive(BliMedRequest::class)

            val spørreundersøkelseId = bliMedRequest.spørreundersøkelseId.tilUUID("spørreundersøkelseId")

            val spørreundersøkelse = redisService.henteSpørreundersøkelse(spørreundersøkelseId)
                ?: throw Feil(feilmelding = "Ukjent spørreundersøkelse $spørreundersøkelseId", feilkode = HttpStatusCode.NotFound)

            val sesjonsId = UUID.randomUUID()
            redisService.lagreSesjon(sesjonsId, spørreundersøkelse.id)

            call.respond(HttpStatusCode.OK, BliMedDTO(
                spørreundersøkelseId = spørreundersøkelse.id.toString(),
                sesjonsId = sesjonsId.toString()
            ))
        }
    }

    rateLimit(RateLimitName("kartlegging")) {
        post(SPØRSMÅL_OG_SVAR_PATH) {
            val spørsmålOgSvarRequest = call.receive(SpørsmålOgSvarRequest::class)

            val id = spørsmålOgSvarRequest.spørreundersøkelseId.tilUUID("spørreundersøkelseId")

            val sesjonsId = spørsmålOgSvarRequest.sesjonsId.tilUUID("sesjonsId")
            if (redisService.henteSpørreundersøkelseIdFraSesjon(sesjonsId) != id)
                throw Feil(feilmelding = "Ugyldig sesjonsId", feilkode = HttpStatusCode.Forbidden)

            val spørreundersøkelse = redisService.henteSpørreundersøkelse(id)
                ?: throw Feil(feilmelding = "Ukjent spørreundersøkelse", feilkode = HttpStatusCode.Forbidden)

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
            val spørreundersøkelse = redisService.henteSpørreundersøkelse(spørreundersøkelseId)
                ?: throw Feil(feilmelding = "Ukjent spørreundersøkelse", feilkode = HttpStatusCode.Forbidden)

            val spørsmål = spørreundersøkelse.spørsmålOgSvaralternativer.firstOrNull { it.id == spørsmålId }
                ?: throw Feil(feilmelding = "Ukjent spørsmål ($spørsmålId)", feilkode = HttpStatusCode.Forbidden)

            if (spørsmål.svaralternativer.none { it.id == svarId })
                throw Feil(feilmelding = "Ukjent svar ($svarId)", feilkode = HttpStatusCode.Forbidden)

            call.application.log.info("Har fått inn svar $svarId")
            kartleggingSvarProdusent.sendSvar(
                KartleggingSvar(
                    spørreundersøkelseId = spørreundersøkelse.id.toString(),
                    sesjonId = sesjonsId.toString(),
                    spørsmålId = spørsmålId.toString(),
                    svarId = svarId.toString()
                )
            )

            call.respond(
                HttpStatusCode.OK,
            )
        }
    }
}

private fun String.tilUUID(hvaErJeg: String) = try{
    UUID.fromString(this)
} catch (e: IllegalArgumentException) {
    throw Feil("Ugyldig formatert UUID $hvaErJeg: $this", e, HttpStatusCode.BadRequest)
}
