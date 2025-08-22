package no.nav.fia.arbeidsgiver.helper

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import no.nav.fia.arbeidsgiver.proxy.samarbeid.SamarbeidService
import org.mockserver.client.MockServerClient
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response
import org.slf4j.Logger
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName

class LydiaApiContainerHelper(
    network: Network = Network.newNetwork(),
    private val log: Logger,
) {
    private val networkAlias = "mockLydiaApiContainer"
    private val port = 7272

    private val dockerImageName = DockerImageName.parse("mockserver/mockserver")
    val container: GenericContainer<*> = GenericContainer(dockerImageName)
        .withNetwork(network)
        .withNetworkAliases(networkAlias)
        .withExposedPorts(port)
        .withLogConsumer(Slf4jLogConsumer(log).withPrefix(networkAlias).withSeparateOutputStreams())
        .withEnv(
            mapOf(
                "MOCKSERVER_LIVENESS_HTTP_GET_PATH" to "/isRunning",
                "SERVER_PORT" to "$port",
                "TZ" to "Europe/Oslo",
            ),
        )
        .waitingFor(Wait.forHttp("/isRunning").forStatusCode(200))
        .apply {
            start()
        }.also {
            log.info("Startet (mock) lydia-api container for network '$network' og port '$port'")
        }

    fun envVars() =
        mapOf(
            "FIA_SAMARBEID_API_URL" to "http://$networkAlias:$port",
        )

    internal fun slettAlleSamarbeid() {
        val client = MockServerClient(
            container.host,
            container.getMappedPort(port),
        )
        client.reset()
    }

    internal fun leggTilSamarbeid(
        orgnr: String,
        iaSamarbeidDto: SamarbeidService.IASamarbeidDto,
    ) {
        log.info("Legger til et samarbeid med id '${iaSamarbeidDto.id}' for orgnr '$orgnr' i mockserver")
        val client = MockServerClient(
            container.host,
            container.getMappedPort(port),
        )

        runBlocking {
            client.`when`(
                request()
                    .withMethod("GET")
                    .withPath("/api/arbeidsgiver/samarbeid/$orgnr"),
            ).respond(
                response().withBody(
                    Json.encodeToString(listOf(iaSamarbeidDto))
                ),
            )
        }
    }
}
