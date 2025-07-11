package no.nav.fia.arbeidsgiver.http.tokenx

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.nimbusds.jose.jwk.RSAKey
import io.ktor.client.call.body
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.Parameters
import no.nav.fia.arbeidsgiver.http.HttpClient.client
import no.nav.fia.arbeidsgiver.konfigurasjon.Miljø
import java.net.URI
import java.time.Instant
import java.util.Date
import java.util.UUID

object TokenExchanger {
    private val privateKey = RSAKey.parse(Miljø.tokenxPrivateJwk).toRSAPrivateKey()

    // Exchange token for Altinn Tilganger hvor det trengs pid/fnr i claim (kommer fra OpenId token)
    internal suspend fun exchangeMedOpenIdToken(
        audience: String,
        openIdToken: String,
    ) = Instant.now().let {
        exchangeToken(
            subjecToken = openIdToken,
            audience = audience,
            now = it,
        )
    }

    // Exchange token for Fia-dokument-publisering hvor det trengs custom claim "tilgang_fia"
    internal suspend fun exchangeMedSelfIssuedToken(
        audience: String,
        scope: Scope,
        orgnr: String,
    ) = Instant.now().let {
        exchangeToken(
            subjecToken = createJwt(
                atInstant = it,
                customClaim = TilgangClaim(scope = scope, orgnr = orgnr)
            ),
            audience = audience,
            now = it,
        )
    }


    private suspend fun exchangeToken(
        subjecToken: String, // Claim-set i subject_token mappes til claim-set i exchange-tokenet
        audience: String,
        now: Instant,
    ): String =
        try {
            val postResponse = client.post(URI.create(Miljø.tokenXTokenEndpoint).toURL()) {
                val clientAssertion = createJwt(atInstant = now)
                setBody(
                    FormDataContent(
                        Parameters.build {
                            append("grant_type", "urn:ietf:params:oauth:grant-type:token-exchange")
                            append("client_assertion_type", "urn:ietf:params:oauth:client-assertion-type:jwt-bearer")
                            append("client_assertion", clientAssertion)
                            append("subject_token_type", "urn:ietf:params:oauth:token-type:jwt")
                            append("subject_token", subjecToken)
                            append("audience", audience)
                        },
                    ),
                )
            }
            val accessToken = postResponse.body<Map<String, String>>()["access_token"]
            val clientOrServerError = postResponse.status.value >= 400
            if (clientOrServerError || accessToken.isNullOrBlank()) {
                // Log response hvis vi får en 4xx eller 5xx statuskode
                val responseInError = if (clientOrServerError) {
                    "Response: '${postResponse.body<String>()}'"
                } else {
                    ""
                }

                throw IllegalStateException(
                    "Fikk ingen token i response, " +
                        "status i response: '${postResponse.status}', response: '$responseInError'"
                )
            } else {
                accessToken
            }
        } catch (e: Exception) {
            throw RuntimeException("Token exchange feil", e)
        }

    private fun createJwt(
        atInstant: Instant,
        customClaim: TilgangClaim? = null,
    ): String {
        return JWT.create().apply {
            withSubject(Miljø.tokenxClientId)
            withIssuer(Miljø.tokenxClientId)
            withAudience(Miljø.tokenXTokenEndpoint)
            withJWTId(UUID.randomUUID().toString())
            withIssuedAt(Date.from(atInstant))
            withNotBefore(Date.from(atInstant))
            withExpiresAt(Date.from(atInstant.plusSeconds(120)))
            if (customClaim != null) withClaim(customClaim.name(), customClaim.value())
        }.sign(Algorithm.RSA256(null, privateKey))
    }
}

enum class Scope(val value: String) {
    DOKUMENT_LESETILGANG("read:dokument"),
    SYKEFRAVARSSTATISTIKK_LESETILGANG("read:sykefravarsstatistikk"),
    SAMARBEID_LESESTILGANG("read:samarbeid"),
}

data class TilgangClaim(
    val orgnr: String,
    val scope: Scope,
) {
    fun name(): String = "tilgang_fia"
    fun value(): String = "${scope.value}:$orgnr"
    override fun toString(): String = value()
}
