package no.nav.fia.arbeidsgiver.konfigurasjon.plugins

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.auth.AuthenticationChecked
import io.ktor.server.response.respond
import kotlinx.serialization.Serializable
import no.nav.fia.arbeidsgiver.http.hentToken
import no.nav.fia.arbeidsgiver.http.orgnr
import no.nav.fia.arbeidsgiver.samarbeidsstatus.api.AltinnTilgangerService
import no.nav.fia.arbeidsgiver.samarbeidsstatus.api.AltinnTilgangerService.Companion.harEnkeltTilgang
import no.nav.fia.arbeidsgiver.samarbeidsstatus.api.AltinnTilgangerService.Companion.harTilgangTilOrgnr

@Suppress("ktlint:standard:function-naming")
fun AltinnAuthorizationPlugin(altinnTilgangerService: AltinnTilgangerService) =
    createRouteScopedPlugin(name = "AuthorizationPlugin") {
        pluginConfig.apply {
            on(AuthenticationChecked) { call ->
                val token = call.request.hentToken() ?: return@on call.respond(HttpStatusCode.Forbidden)
                val altinnTilganger = altinnTilgangerService.hentAltinnTilganger(token = token)
                val orgnr = call.orgnr ?: return@on call.respond(HttpStatusCode.BadRequest)

                if (!altinnTilganger.harTilgangTilOrgnr(orgnr)) {
                    call.respond(
                        status = HttpStatusCode.Forbidden,
                        message = ResponseIError(message = "Ikke tilgang til orgnummer"),
                    )
                }

                if (!altinnTilganger.harEnkeltTilgang(orgnr)) {
                    call.respond(
                        status = HttpStatusCode.Forbidden,
                        message = ResponseIError(message = "Ikke tilgang til enkelttilgang for orgnummer"),
                    )
                }
            }
        }
    }

@Serializable
data class ResponseIError(
    val message: String,
)
