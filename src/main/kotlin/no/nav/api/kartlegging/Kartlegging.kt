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
import no.nav.persistence.RedisService
import java.lang.IllegalArgumentException
import java.util.*

const val KARTLEGGING_PATH = "/fia-arbeidsgiver/kartlegging"
const val BLI_MED_PATH = "${KARTLEGGING_PATH}/bli-med"
const val SPØRSMÅL_OG_SVAR_PATH = "${KARTLEGGING_PATH}/sporsmal-og-svar"

fun Route.kartlegging(redisService: RedisService) {
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
            if (spørreundersøkelse.pinKode != bliMedRequest.pinkode)
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

            if (redisService.henteSesjon(sesjonsId) != id) {
                return@post call.loggOgSendFeil("ugyldig sesjonsId", HttpStatusCode.Forbidden, id.toString())
            }

            val spørreundersøkelse = redisService.henteSpørreundersøkelse(id)
                ?: return@post call.loggOgSendFeil("ukjent spørreundersøkelse", HttpStatusCode.Forbidden, id.toString())

            call.respond(
                HttpStatusCode.OK,
                SpørsmålOgSvaralternativerDTO.toDto(spørreundersøkelse.spørsmålOgSvaralternativer)
            )
        }
    }
}

private fun ApplicationCall.loggOgSendFeil(beskrivelse: String, httpStatusCode: HttpStatusCode, spørreundersøkelseId : String?){
    application.log.warn("Ugyldig forsøk $beskrivelse med STATUSKODE: $httpStatusCode, og ID: $spørreundersøkelseId")
    response.status(httpStatusCode)
}
