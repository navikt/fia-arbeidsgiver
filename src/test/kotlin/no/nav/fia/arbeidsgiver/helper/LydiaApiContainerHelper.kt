package no.nav.fia.arbeidsgiver.helper

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import no.nav.fia.arbeidsgiver.helper.HttpMockServerContainerUtils.Companion.createMockServerClient
import no.nav.fia.arbeidsgiver.helper.HttpMockServerContainerUtils.Companion.resetAllExpectations
import no.nav.fia.arbeidsgiver.proxy.samarbeid.DokumentMetadata
import no.nav.fia.arbeidsgiver.proxy.samarbeid.SamarbeidMedDokumenterDto.Companion.Status
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

class LydiaApiContainerHelper(
    network: Network = Network.newNetwork(),
    private val log: Logger,
) {
    private val networkAlias = "lydia-api-container"
    private val port =
        PORT // mockserver default port er 1080 som MockServerContainer() eksponerer selv med "this.addExposedPort(1080);"
    private var mockServerClient: MockServerClient? = null

    private val dockerImageName = DockerImageName.parse("xdevsoftware/mockserver:1.0.19")
    val container: GenericContainer<*> = MockServerContainer(dockerImageName).withNetwork(network)
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
            log.info("Startet (mock) lydia-api container for network '$network' og port '$port'")
        }

    fun envVars() =
        mapOf(
            "FIA_SAMARBEID_API_URL" to "http://$networkAlias:$port",
        )

    internal fun slettAlleSamarbeid() = resetAllExpectations(client = mockServerClient!!)

    internal fun leggTilSamarbeid(
        orgnr: String,
        iaSamarbeidDto: SamarbeidMedDokumenterV1Dto,
    ) {
        log.info("Legger til et samarbeid med offentligId '${iaSamarbeidDto.offentligId}' for orgnr '$orgnr' i mockserver")

        runBlocking {
            mockServerClient!!.`when`(
                request()
                    .withMethod("GET")
                    .withPath("/api/arbeidsgiver/samarbeid/$orgnr"),
            ).respond(
                response().withBody(
                    Json.encodeToString(listOf(iaSamarbeidDto)),
                ),
            )
        }
    }

    @Serializable
    data class SamarbeidMedDokumenterV1Dto(
        // Ved å bruke denne versjonen av DTOen i testene sjekker vi at APIet ikke er avhengig av felter som ikke er i bruk
        @Deprecated("Blir fjernet i en senere versjon. Bruk offentligId istedenfor id")
        val id: Int,
        val offentligId: String,
        val navn: String,
        val status: Status,
        val sistEndret: LocalDateTime? = null,
        val dokumenter: List<DokumentMetadata> = emptyList(),
    )
}
