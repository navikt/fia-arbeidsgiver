package no.nav.fia.arbeidsgiver.helper

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import no.nav.fia.arbeidsgiver.proxy.dokument.DokumentService
import org.mockserver.client.MockServerClient
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response
import org.slf4j.Logger
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName

class DokumentPubliseringContainerHelper(
    network: Network = Network.newNetwork(),
    private val log: Logger,
) {
    companion object {

        fun lagJsonForDokumenterMock(
            dokument: DokumentService.DokumentDto
        ): String = Json.encodeToString(listOf(dokument))
    }

    private val networkAlias = "mockFiaDokumentPubliseringContainer"
    private val port = 7171

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
            log.info("Startet (mock) fia-dokument-publisering container for network '$network' og port '$port'")
        }

    fun envVars() =
        mapOf(
            "FIA_DOKUMENT_PUBLISERING_URL" to "http://$networkAlias:$port",
        )

    internal fun slettAlleDokumenter() {
        val client = MockServerClient(
            container.host,
            container.getMappedPort(port),
        )
        client.reset()
    }

    internal fun leggTilDokument(
        orgnr: String,
        dokument: DokumentService.DokumentDto,
    ) {
        log.info("Legger til et dokument for orgnr '$orgnr'")
        val client = MockServerClient(
            container.host,
            container.getMappedPort(port),
        )

        runBlocking {
            client.`when`(
                request()
                    .withMethod("GET")
                    .withPath("/dokumenter/$orgnr"),
            ).respond(
                response().withBody(
                    lagJsonForDokumenterMock(dokument = dokument)
                ),
            )
        }
    }
}
