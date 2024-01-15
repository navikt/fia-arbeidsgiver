package no.nav.api.kartlegging

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.log
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
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

            val id = try {
                UUID.fromString(bliMedRequest.spørreundersøkelseId)
            } catch (e: IllegalArgumentException) {
                return@post call.loggOgSendFeil("ugyldig formatert id", HttpStatusCode.BadRequest, bliMedRequest.spørreundersøkelseId)
            }

            val spørreundersøkelse = redisService.henteSpørreundersøkelse(id)
                ?: return@post call.loggOgSendFeil("ukjent spørreundersøkelse", HttpStatusCode.NotFound, id.toString())
            if (spørreundersøkelse.pinkode != bliMedRequest.pinkode)
                return@post call.loggOgSendFeil("feil pinkode", HttpStatusCode.Forbidden, id.toString())

            val sesjonsId = UUID.randomUUID()
            redisService.lagreSesjon(sesjonsId, spørreundersøkelse.id)

            call.respond(HttpStatusCode.OK, BliMedDTO(
                id = spørreundersøkelse.id.toString(),
                sesjonsId = sesjonsId.toString()
            ))
        }
    }

    rateLimit(RateLimitName("kartlegging")) {
        post(SPØRSMÅL_OG_SVAR_PATH) {
            val spørsmålOgSvarRequest = call.receive(SpørsmålOgSvarRequest::class)

            val id = try {
                UUID.fromString(spørsmålOgSvarRequest.spørreundersøkelseId)
            } catch (e: IllegalArgumentException) {
                return@post call.loggOgSendFeil("ugyldig formatert id", HttpStatusCode.BadRequest, spørsmålOgSvarRequest.spørreundersøkelseId)
            }

            val sesjonsId = try {
                UUID.fromString(spørsmålOgSvarRequest.sesjonsId)
            } catch (e: IllegalArgumentException) {
                return@post call.loggOgSendFeil("ugyldig formatert sesjonsId", HttpStatusCode.BadRequest, id.toString())
            }

            if (redisService.henteSpørreundersøkelseIdFraSesjon(sesjonsId) != id) {
                return@post call.loggOgSendFeil("ugyldig sesjonsId", HttpStatusCode.Forbidden, id.toString())
            }

            val spørreundersøkelse = redisService.henteSpørreundersøkelse(id)
                ?: return@post call.loggOgSendFeil("ukjent spørreundersøkelse", HttpStatusCode.Forbidden, id.toString())

            call.respond(
                HttpStatusCode.OK,
                SpørsmålOgSvaralternativerDTO.toDto(spørreundersøkelse.spørsmålOgSvaralternativer)
            )
        }

        post(SVAR_PATH) {
            val svarRequest = call.receive(SvarRequest::class)

            val spørreundersøkelseId = try {
                UUID.fromString(svarRequest.spørreundersøkelseId)
            } catch (e: IllegalArgumentException) {
                return@post call.loggOgSendFeil("ugyldig formatert id", HttpStatusCode.BadRequest, svarRequest.spørreundersøkelseId)
            }

            val sesjonsId = try {
                UUID.fromString(svarRequest.sesjonsId)
            } catch (e: IllegalArgumentException) {
                return@post call.loggOgSendFeil("ugyldig formatert sesjonsId (${svarRequest.sesjonsId})", HttpStatusCode.BadRequest, svarRequest.spørsmålId)
            }

            if (redisService.henteSpørreundersøkelseIdFraSesjon(sesjonsId) != spørreundersøkelseId) {
                return@post call.loggOgSendFeil("ugyldig sesjonsId", HttpStatusCode.Forbidden, spørreundersøkelseId.toString())
            }

            val spørsmålId = try {
                UUID.fromString(svarRequest.spørsmålId)
            } catch (e: IllegalArgumentException) {
                return@post call.loggOgSendFeil("ugyldig formatert spørsmålId", HttpStatusCode.BadRequest, svarRequest.spørsmålId)
            }

            val svarId = try {
                UUID.fromString(svarRequest.svarId)
            } catch (e: IllegalArgumentException) {
                return@post call.loggOgSendFeil("ugyldig formatert svarId", HttpStatusCode.BadRequest, svarRequest.svarId)
            }

            val spørreundersøkelse = redisService.henteSpørreundersøkelse(spørreundersøkelseId)
                ?: return@post call.loggOgSendFeil("ukjent spørreundersøkelse", HttpStatusCode.Forbidden, spørreundersøkelseId.toString())

            val spørsmål = spørreundersøkelse.spørsmålOgSvaralternativer.firstOrNull { it.id == spørsmålId }
                ?: return@post call.loggOgSendFeil("ukjent spørsmål ($spørsmålId)", HttpStatusCode.Forbidden, spørreundersøkelseId.toString())

            if (spørsmål.svaralternativer.none { it.id == svarId }) {
                return@post call.loggOgSendFeil("ukjent svar ($svarId)", HttpStatusCode.Forbidden, spørreundersøkelseId.toString())
            }

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

private fun ApplicationCall.loggOgSendFeil(beskrivelse: String, httpStatusCode: HttpStatusCode, spørreundersøkelseId : String?){
    application.log.warn("Ugyldig forsøk $beskrivelse med STATUSKODE: $httpStatusCode, og ID: $spørreundersøkelseId")
    response.status(httpStatusCode)
}
