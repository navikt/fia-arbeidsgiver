package no.nav.api.kartlegging

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
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
        post("$BLI_MED_PATH/{id}") {
            val id = call.parameters["id"] ?: return@post call.response.status(HttpStatusCode.NotFound)
            val pin = call.receive(String::class)

            val uuid = try {
                UUID.fromString(id)
            } catch (e: IllegalArgumentException) {
                return@post call.response.status(HttpStatusCode.BadRequest)
            }

            val spørreundersøkelse = redisService.henteSpørreundersøkelse(uuid)
                ?: return@post call.response.status(HttpStatusCode.NotFound)
            if (spørreundersøkelse.pinKode != pin)
                return@post call.response.status(HttpStatusCode.Unauthorized)

            val sesjonsId = UUID.randomUUID()
            redisService.lagreSesjon(sesjonsId, spørreundersøkelse.id)

            call.respond(HttpStatusCode.OK, BliMedDTO(
                id = spørreundersøkelse.id.toString(),
                sesjonsId = sesjonsId.toString()
            ))
        }
    }

    post("$SPØRSMÅL_OG_SVAR_PATH/{id}") {
        val idString = call.parameters["id"] ?: return@post call.response.status(HttpStatusCode.NotFound)
        val sesjonsIdString = call.receive(String::class)

        val id = try {
            UUID.fromString(idString)
        } catch (e: IllegalArgumentException) {
            return@post call.response.status(HttpStatusCode.BadRequest)
        }

        val sesjonsId = try {
            UUID.fromString(sesjonsIdString)
        } catch (e: IllegalArgumentException) {
            return@post call.response.status(HttpStatusCode.BadRequest)
        }

        if (redisService.henteSesjon(sesjonsId) != id) {
            return@post call.response.status(HttpStatusCode.Unauthorized)
        }

        val spørreundersøkelse = redisService.henteSpørreundersøkelse(id)
            ?: return@post call.response.status(HttpStatusCode.NotFound)

        call.respond(HttpStatusCode.OK,
            spørreundersøkelse.spørsmålOgSvaralternativer)
    }

}
