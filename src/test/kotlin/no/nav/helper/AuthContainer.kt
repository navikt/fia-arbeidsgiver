package no.nav.helper

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jwt.SignedJWT
import com.nimbusds.oauth2.sdk.AuthorizationCode
import com.nimbusds.oauth2.sdk.AuthorizationCodeGrant
import com.nimbusds.oauth2.sdk.TokenRequest
import com.nimbusds.oauth2.sdk.auth.ClientSecretBasic
import com.nimbusds.oauth2.sdk.auth.Secret
import com.nimbusds.oauth2.sdk.id.ClientID
import no.nav.security.mock.oauth2.OAuth2Config
import no.nav.security.mock.oauth2.token.DefaultOAuth2TokenCallback
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.net.URI
import java.util.*

class AuthContainer(network: Network) {
    private val port = "6969"
    private val issuerName = "default"
    private val networkalias = "authserver"
    private val baseEndpointUrl = "http://$networkalias:$port"
    private val oAuth2Config = OAuth2Config()

    val container = GenericContainer(DockerImageName.parse("ghcr.io/navikt/mock-oauth2-server:0.5.8"))
        .withNetwork(network)
        .withNetworkAliases(networkalias)
        .withLogConsumer(Slf4jLogConsumer(TestContainerHelper.log).withPrefix("authContainer").withSeparateOutputStreams())
        .withExposedPorts(6969)
        .withEnv(
            mapOf(
                "SERVER_PORT" to port,
                "TZ" to "Europe/Oslo"
            )
        )
        .waitingFor(Wait.forHttp("/default/.well-known/openid-configuration").forStatusCode(200))
        .apply { start() }

    internal fun issueToken(
        issuerId: String = issuerName,
        subject: String = UUID.randomUUID().toString(),
        audience: String,
        claims: Map<String, Any> = emptyMap(),
        expiry: Long = 3600
    ): SignedJWT {
        val issuerUrl = "$baseEndpointUrl/$issuerName"
        val tokenCallback = DefaultOAuth2TokenCallback(
            issuerId,
            subject,
            JOSEObjectType.JWT.type,
            listOf(audience),
            claims,
            expiry
        )

        val tokenRequest = TokenRequest(
            URI.create(baseEndpointUrl),
            ClientSecretBasic(ClientID(issuerName), Secret("secret")),
            AuthorizationCodeGrant(AuthorizationCode("123"), URI.create("http://localhost"))
        )
        return oAuth2Config.tokenProvider.accessToken(tokenRequest, issuerUrl.toHttpUrl(), tokenCallback, null)
    }

    fun getEnv() = mapOf(
        "TOKEN_X_CLIENT_ID" to "hei",
        "TOKEN_X_ISSUER" to "http://$networkalias:$port/default",
        "TOKEN_X_JWKS_URI" to "http://$networkalias:$port/default/jwks",
        "TOKEN_X_TOKEN_ENDPOINT" to "http://$networkalias:$port/default/token",
        "TOKEN_X_PRIVATE_JWK" to "",
    )
}