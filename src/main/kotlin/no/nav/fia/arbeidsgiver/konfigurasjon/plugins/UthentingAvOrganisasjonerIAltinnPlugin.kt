package no.nav.fia.arbeidsgiver.konfigurasjon.plugins

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.auth.AuthenticationChecked
import io.ktor.server.response.respond
import io.ktor.util.AttributeKey
import no.nav.fia.arbeidsgiver.http.hentToken
import no.nav.fia.arbeidsgiver.samarbeidsstatus.api.AltinnTilgangerService
import org.slf4j.LoggerFactory

@Suppress("ktlint:standard:function-naming")
fun UthentingAvOrganisasjonerIAltinnPlugin(altinnTilgangerService: AltinnTilgangerService) =
    createRouteScopedPlugin(name = "UthentingAvOrganisasjonerIAltinnPlugin") {
        pluginConfig.apply {
            val logger = LoggerFactory.getLogger(this::class.java)
            logger.debug("Henter organisasjoner i Altinn")

            on(hook = AuthenticationChecked) { call ->
                val token = call.request.hentToken() ?: return@on call.respond(HttpStatusCode.Forbidden)
                val altinnTilganger = altinnTilgangerService.hentAltinnTilganger(token = token)

                call.attributes.put(
                    AltinnTilgangerKey,
                    AltinnTilgangerOnCall(
                        altinnTilganger = altinnTilganger,
                    ),
                )
            }
        }
    }

val AltinnTilgangerKey = AttributeKey<AltinnTilgangerOnCall>("AltinnTilganger")

data class AltinnTilgangerOnCall(
    val altinnTilganger: AltinnTilgangerService.AltinnTilganger?,
)
