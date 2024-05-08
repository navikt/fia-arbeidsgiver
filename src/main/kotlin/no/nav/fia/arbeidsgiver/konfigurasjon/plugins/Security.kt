package no.nav.fia.arbeidsgiver.konfigurasjon.plugins

import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.interfaces.Claim
import com.auth0.jwt.interfaces.DecodedJWT
import io.ktor.server.application.Application
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import no.nav.fia.arbeidsgiver.konfigurasjon.Miljø
import java.net.URI
import java.util.concurrent.TimeUnit

fun Application.configureSecurity() {
    val tokenxJwkProvider = JwkProviderBuilder(URI(Miljø.tokenxJwksUri).toURL())
        .cached(10, 24, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()

    val azureJwkProvider = JwkProviderBuilder(URI(Miljø.azureJwksUri).toURL())
        .cached(10, 24, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()

    authentication {
        jwt(name = "tokenx") {
            val tokenFortsattGyldigFørUtløpISekunder = 3L
            verifier(tokenxJwkProvider, issuer = Miljø.tokenxIssuer) {
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

        jwt(name = "azure") {
            val tokenFortsattGyldigFørUtløpISekunder = 3L
            verifier(azureJwkProvider, issuer = Miljø.azureIssuer) {
                acceptLeeway(tokenFortsattGyldigFørUtløpISekunder)
                withAudience(Miljø.azureClientId)
                withClaimPresence("NAVident")
                withClaim("groups") { claim: Claim, _: DecodedJWT ->
                    claim.asList(String::class.java).contains(Miljø.saksbehandlerGruppe) ||
                    claim.asList(String::class.java).contains(Miljø.superbrukerGruppe)
                }
            }
            validate { token ->
                JWTPrincipal(token.payload)
            }
        }
    }
}
