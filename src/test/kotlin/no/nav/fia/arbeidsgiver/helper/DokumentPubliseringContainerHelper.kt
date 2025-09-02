package no.nav.fia.arbeidsgiver.helper

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import no.nav.fia.arbeidsgiver.helper.HttpMockServerContainerUtils.Companion.createMockServerClient
import no.nav.fia.arbeidsgiver.helper.HttpMockServerContainerUtils.Companion.resetAllExpectations
import no.nav.fia.arbeidsgiver.proxy.dokument.DokumentService
import org.slf4j.Logger
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import software.xdev.mockserver.client.MockServerClient
import software.xdev.mockserver.model.HttpRequest.request
import software.xdev.mockserver.model.HttpResponse.response
import software.xdev.testcontainers.mockserver.containers.MockServerContainer
import software.xdev.testcontainers.mockserver.containers.MockServerContainer.PORT

class DokumentPubliseringContainerHelper(
    network: Network = Network.newNetwork(),
    private val log: Logger,
) {
    companion object {
        fun lagJsonForDokumentMock(dokument: DokumentService.DokumentDto): String = Json.encodeToString(dokument)
    }

    private val networkAlias = "fia-dokument-publisering-container"
    private val port =
        PORT // mockserver default port er 1080 som MockServerContainer() eksponerer selv med "this.addExposedPort(1080);"
    private var mockServerClient: MockServerClient? = null

    private val dockerImageName = DockerImageName.parse("xdevsoftware/mockserver:1.0.19")
    val container: GenericContainer<*> = MockServerContainer(dockerImageName)
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
            mockServerClient = createMockServerClient(container = it, port = port)
            log.info("Startet (mock) fia-dokument-publisering container for network '$network' og port '$port'")
        }

    fun envVars() =
        mapOf(
            "FIA_DOKUMENT_PUBLISERING_URL" to "http://$networkAlias:$port",
        )

    internal fun slettAlleDokumenter() = resetAllExpectations(client = mockServerClient!!)

    internal fun leggTilDokument(
        orgnr: String,
        dokument: DokumentService.DokumentDto,
    ) {
        log.info("Legger til et dokument med dokumentId '${dokument.dokumentId}' for orgnr '$orgnr' i mockserver")

        runBlocking {
            mockServerClient!!.`when`(
                request()
                    .withMethod("GET")
                    .withPath("/dokument/orgnr/$orgnr/dokumentId/${dokument.dokumentId}"),
            ).respond(
                response().withBody(
                    lagJsonForDokumentMock(dokument = dokument),
                ),
            )
        }
    }
}
