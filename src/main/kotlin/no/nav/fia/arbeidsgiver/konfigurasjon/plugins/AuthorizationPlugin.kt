package no.nav.fia.arbeidsgiver.konfigurasjon.plugins

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.auth.AuthenticationChecked
import io.ktor.server.response.respond
import kotlinx.serialization.Serializable
import no.nav.fia.arbeidsgiver.http.hentToken
import no.nav.fia.arbeidsgiver.http.orgnr
import no.nav.fia.arbeidsgiver.samarbeidsstatus.api.AltinnTilgangerService
import no.nav.fia.arbeidsgiver.samarbeidsstatus.api.AltinnTilgangerService.Companion.harEnkeltrettighet
import no.nav.fia.arbeidsgiver.samarbeidsstatus.api.AltinnTilgangerService.Companion.harTilgangTilOrgnr
import org.slf4j.LoggerFactory

@Suppress("ktlint:standard:function-naming")
fun AltinnAuthorizationPlugin(altinnTilgangerService: AltinnTilgangerService) =
    createRouteScopedPlugin(name = "AuthorizationPlugin") {
        pluginConfig.apply {
            val logger = LoggerFactory.getLogger(this::class.java)
            on(AuthenticationChecked) { call ->
                val token = call.request.hentToken() ?: return@on call.respond(HttpStatusCode.Forbidden)
                val altinnTilganger = altinnTilgangerService.hentAltinnTilganger(token = token)
                val orgnr = call.orgnr ?: return@on call.respond(HttpStatusCode.BadRequest)

                if (!altinnTilganger.harTilgangTilOrgnr(orgnr)) {
                    logger.info("Ikke tilgang til orgnummer")
                    call.respond(
                        status = HttpStatusCode.Forbidden,
                        message = ResponseIError(message = "Ikke tilgang til orgnummer"),
                    )
                    return@on
                }

                if (!altinnTilganger.harEnkeltrettighet(orgnr)) {
                    logger.info("Ikke tilgang til enkeltrettighet")
                    call.respond(
                        status = HttpStatusCode.Forbidden,
                        message = ResponseIError(message = "Ikke tilgang til orgnummer"),
                    )
                    return@on
                }
            }
        }
    }

@Serializable
data class ResponseIError(
    val message: String,
)
