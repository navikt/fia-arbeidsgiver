package no.nav.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import no.nav.konfigurasjon.Miljø
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.AltinnrettigheterProxyKlient
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.AltinnrettigheterProxyKlientConfig
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.ProxyConfig
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.Subject
import no.nav.arbeidsgiver.altinnrettigheter.proxy.klient.model.TokenXToken
import no.nav.tokenx.TokenExchanger
import no.nav.utvidelser.hentToken
import no.nav.utvidelser.orgnr
import no.nav.utvidelser.tokenSubject


val AuthorizationPlugin = createRouteScopedPlugin(
    name = "AuthorizationPlugin",
) {
    pluginConfig.apply {
        on(AuthenticationChecked) { call ->
            val fnr = call.request.tokenSubject() ?: return@on call.respond(HttpStatusCode.Forbidden)
            val token = call.request.hentToken() ?: return@on call.respond(HttpStatusCode.Forbidden)
            val orgnr = call.orgnr ?: return@on call.respond(HttpStatusCode.BadRequest)


            val altinnKlient = AltinnrettigheterProxyKlient(
                AltinnrettigheterProxyKlientConfig(
                    ProxyConfig("fia-arbeidsgiver", Miljø.altinnProxyUrl)
                )
            )

            val virksomheterSomBrukerHarTilgangTil = altinnKlient.hentOrganisasjoner(
                TokenXToken(
                    TokenExchanger.exchangeToken(
                        token = token,
                        audience = Miljø.altinnRettigheterProxyClientId
                    )
                ),
                Subject(fnr),
                true
            )

            if (virksomheterSomBrukerHarTilgangTil.none { it.organizationNumber == orgnr })
                call.respond(status = HttpStatusCode.Forbidden, message = "Ikke tilgang til orgnummer")
        }
    }
}