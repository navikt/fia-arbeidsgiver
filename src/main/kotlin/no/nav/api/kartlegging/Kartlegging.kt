package no.nav.api.kartlegging

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.persistence.RedisService
import java.util.*

const val PATH = "/fia-arbeidsgiver/kartlegging/bli-med"

fun Route.kartlegging(redisService: RedisService) {
    post("$PATH/{id}") {
        val id = call.parameters["id"] ?: return@post call.response.status(HttpStatusCode.NotFound)
        val pin = call.receive(String::class)

        val spørreundersøkelse = redisService.henteSpørreundersøkelse(UUID.fromString(id))
            ?: return@post call.response.status(HttpStatusCode.NotFound)
        if (spørreundersøkelse.pinKode != pin)
            return@post call.response.status(HttpStatusCode.Unauthorized)

        call.respond(HttpStatusCode.OK, SpørreundersøkelseDTO(
            id = spørreundersøkelse.id.toString(),
            sesjonsId = UUID.randomUUID().toString()
        ))
    }

}