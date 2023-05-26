package no.nav.plugins

import com.auth0.jwk.JwkProviderBuilder
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import no.nav.Miljø
import java.net.URI
import java.util.concurrent.TimeUnit

fun Application.configureSecurity() {
    authentication {
        val jwkProvider = JwkProviderBuilder(URI(Miljø.tokenxJwkPath).toURL())
            .cached(10, 24, TimeUnit.HOURS)
            .rateLimited(10, 1, TimeUnit.MINUTES)
            .build()
        jwt(name = "tokenx") {
            val tokenFortsattGyldigFørUtløpISekunder = 3L
            verifier(jwkProvider, issuer = Miljø.tokenxIssuer) {
                acceptLeeway(tokenFortsattGyldigFørUtløpISekunder)
                withAudience(Miljø.tokenxClientId)
                withClaim("acr", "Level4")
                withClaimPresence("sub")
            }
            validate { token ->
                application.log.debug("Validerer token ${Json.encodeToString(token)}")
                val principal = JWTPrincipal(token.payload)
                application.log.debug("pricipal: ${Json.encodeToString(principal)}")
                principal
            }
        }
    }
}
