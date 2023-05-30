package no.nav.plugins

import com.auth0.jwk.JwkProviderBuilder
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import no.nav.Miljø
import java.net.URI
import java.util.concurrent.TimeUnit

fun Application.configureSecurity() {
    val jwkProvider = JwkProviderBuilder(URI(Miljø.idportenJwkPath).toURL())
        .cached(10, 24, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()
    authentication {
        jwt(name = "tokenx") {
            val tokenFortsattGyldigFørUtløpISekunder = 3L
            verifier(jwkProvider, issuer = Miljø.idportenIssuer) {
                acceptLeeway(tokenFortsattGyldigFørUtløpISekunder)
                withAudience(Miljø.idportenAudience)
                withClaim("acr", "Level4")
                withClaim("client_id", Miljø.idportenClientId)
                withClaimPresence("pid")
            }
            validate { token ->
                application.log.info("TOKEN Issuer: ${token.issuer}, Audience: ${token.audience}, acr: ${token["acr"]}, sub: ${token["sub"]}")
                val principal = JWTPrincipal(token.payload)
                application.log.info("PRINCIPAL nbf: ${principal.notBefore}, iat: ${principal.issuedAt}, exp: ${principal.expiresAt}")
                principal
            }
        }
    }
}
