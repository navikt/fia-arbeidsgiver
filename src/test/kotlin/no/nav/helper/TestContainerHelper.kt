package no.nav.helper

import io.kotest.matchers.string.shouldContain
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.http.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.Network
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy
import org.testcontainers.images.builder.ImageFromDockerfile
import java.time.Duration
import kotlin.io.path.Path

class TestContainerHelper {
    companion object {
        val log: Logger = LoggerFactory.getLogger(TestContainerHelper::class.java)
        val network = Network.newNetwork()

        val authServer = AuthContainer(network)
        val kafka = KafkaContainer(network)
        val redis = RedisContainer(network)
        val altinnProxy = AltinnProxyContainer()

        val fiaArbeidsgiverApi =
            GenericContainer(
                ImageFromDockerfile().withDockerfile(Path("./Dockerfile"))
            )
                .withNetwork(network)
                .withExposedPorts(8080)
                .withLogConsumer(Slf4jLogConsumer(log).withPrefix("fiaArbeidsgiver").withSeparateOutputStreams())
                .withEnv(
                    authServer.getEnv() +
                            altinnProxy.getEnv() +
                            kafka.getEnv() +
                            redis.getEnv() +
                        mapOf(
                            "NAIS_CLUSTER_NAME" to "lokal"
                        )
                )
                .dependsOn(authServer.container, kafka.container, redis.container)
                .waitingFor(HttpWaitStrategy().forPath("/internal/isalive").withStartupTimeout(Duration.ofSeconds(20)))
                .apply {
                    start()
                }

        internal fun accessToken(
            subject: String = "123",
            audience: String = "hei",
            claims: Map<String, String> = mapOf(
                "acr" to "Level4",
                "pid" to subject,
                "client_id" to "hei",
            ),
        ) = authServer.issueToken(
            subject = subject,
            audience = audience,
            claims = claims
        )

        infix fun GenericContainer<*>.shouldContainLog(regex: Regex) = logs shouldContain regex
    }
}

private val httpClient = HttpClient(CIO)

private suspend fun GenericContainer<*>.performRequest(
    url: String,
    config: HttpRequestBuilder.() -> Unit = {}
) =
    httpClient.request {
        config()
        header(HttpHeaders.Accept, "application/json")
        url {
            protocol = URLProtocol.HTTP
            host = this@performRequest.host
            port = firstMappedPort
            path(url)
        }
    }

internal fun withToken(): HttpRequestBuilder.() -> Unit = {
    header(HttpHeaders.Authorization, "Bearer ${TestContainerHelper.accessToken().serialize()}")
}

internal suspend fun GenericContainer<*>.performGet(url: String, config: HttpRequestBuilder.() -> Unit = {}) =
    performRequest(url) {
        config()
        method = HttpMethod.Get
    }
