package no.nav.plugins

import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.interfaces.Claim
import com.auth0.jwt.interfaces.DecodedJWT
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import no.nav.konfigurasjon.Miljø
import java.net.URI
import java.util.concurrent.TimeUnit

fun Application.configureSecurity() {
    val jwkProvider = JwkProviderBuilder(URI(Miljø.tokenxJwkPath).toURL())
        .cached(10, 24, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()
    authentication {
        jwt(name = "tokenx") {
            val tokenFortsattGyldigFørUtløpISekunder = 3L
            verifier(jwkProvider, issuer = Miljø.tokenxIssuer) {
                acceptLeeway(tokenFortsattGyldigFørUtløpISekunder)
                withAudience(Miljø.tokenxClientId)
                withClaim("acr") { claim: Claim, _: DecodedJWT ->
                    claim.asString().equals("Level4") || claim.asString().equals("idporten-loa-high")
                }
                withClaimPresence("pid")
            }
            validate { token ->
                JWTPrincipal(token.payload)
            }
        }
    }
}
