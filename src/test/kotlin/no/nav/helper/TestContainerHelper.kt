package no.nav.helper

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.Network
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.images.builder.ImageFromDockerfile
import org.testcontainers.utility.DockerImageName
import java.time.Duration
import kotlin.io.path.Path

class TestContainerHelper {
    companion object {
        val log: Logger = LoggerFactory.getLogger(TestContainerHelper::class.java)
        val network = Network.newNetwork()

        val authContainer = GenericContainer(DockerImageName.parse("ghcr.io/navikt/mock-oauth2-server:0.5.7"))
            .withNetwork(network)
            .withNetworkAliases("authserver")
            .withLogConsumer(Slf4jLogConsumer(log).withPrefix("authContainer - ").withSeparateOutputStreams())
            .withExposedPorts(6969)
            .withEnv(
                mapOf(
                    "SERVER_PORT" to "6969",
                    "TZ" to "Europe/Oslo"
                )
            )
            .waitingFor(Wait.forHttp("/default/.well-known/openid-configuration").forStatusCode(200))
            .apply { start() }

        val fiaArbeidsgiverApi =
            GenericContainer(
                ImageFromDockerfile().withDockerfile(Path("./Dockerfile"))
            )
            .withNetwork(network)
            .withExposedPorts(8080)
            .withLogConsumer(Slf4jLogConsumer(log).withPrefix("fiaArbeidsgiver - ").withSeparateOutputStreams())
            .withEnv(mapOf(
                "TOKEN_X_CLIENT_ID" to "hei",
                "TOKEN_X_ISSUER" to "http://authserver:6969/default",
                "TOKEN_X_JWKS_URI" to "http://authserver:6969/default/jwks",
                "TOKEN_X_TOKEN_ENDPOINT" to "http://authserver:6969/default/token",
                "TOKEN_X_PRIVATE_JWK" to "",
            ))
            .waitingFor(HttpWaitStrategy().forPath("/internal/isalive").withStartupTimeout(Duration.ofSeconds(20)))
            .apply {
                start()
            }
    }
}


suspend fun GenericContainer<*>.performGet(url: String) =
    HttpClient(CIO)
        .get("http://$host:${getMappedPort(8080)}/$url")
