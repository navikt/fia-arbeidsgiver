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

    internal suspend fun exchangeToken(
        subjectToken: String, // Claim-set i subject_token mappes til claim-set i exchange-tokenet
        audience: String,
    ): String =
        try {
            val postResponse = client.post(URI.create(Miljø.tokenXTokenEndpoint).toURL()) {
                val clientAssertion = createJwt(atInstant = Instant.now())
                setBody(
                    FormDataContent(
                        Parameters.build {
                            append("grant_type", "urn:ietf:params:oauth:grant-type:token-exchange")
                            append("client_assertion_type", "urn:ietf:params:oauth:client-assertion-type:jwt-bearer")
                            append("client_assertion", clientAssertion)
                            append("subject_token_type", "urn:ietf:params:oauth:token-type:jwt")
                            append("subject_token", subjectToken)
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
    ): String {
        return JWT.create().apply {
            withSubject(Miljø.tokenxClientId)
            withIssuer(Miljø.tokenxClientId)
            withAudience(Miljø.tokenXTokenEndpoint)
            withJWTId(UUID.randomUUID().toString())
            withIssuedAt(Date.from(atInstant))
            withNotBefore(Date.from(atInstant))
            withExpiresAt(Date.from(atInstant.plusSeconds(120)))
        }.sign(Algorithm.RSA256(null, privateKey))
    }
}
