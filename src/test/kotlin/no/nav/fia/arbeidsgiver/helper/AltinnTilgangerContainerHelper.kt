package no.nav.fia.arbeidsgiver.helper

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import no.nav.fia.arbeidsgiver.helper.HttpMockServerContainerUtils.Companion.createMockServerClient
import no.nav.fia.arbeidsgiver.helper.HttpMockServerContainerUtils.Companion.resetAllExpectations
import no.nav.fia.arbeidsgiver.samarbeidsstatus.api.AltinnTilgangerService
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

class AltinnTilgangerContainerHelper(
    network: Network = Network.newNetwork(),
    private val log: Logger,
) {
    companion object {
        const val ALTINN_ORGNR_1 = "311111111"
        const val ALTINN_ORGNR_2 = "322222222"
        const val ALTINN_OVERORDNET_ENHET = "400000000"
        const val ORGNR_UTEN_TILKNYTNING = "300000000"

        fun lagJsonForAltinnTilgangerMock(
            overordnetEnhet: String,
            underenheterMedRettighet: List<OrgnrMedEnkeltrettigheter>,
            erOverordnetEnhetSlettet: Boolean,
        ): String =
            Json.encodeToString(
                AltinnTilgangerService.AltinnTilganger(
                    hierarki = listOf(
                        AltinnTilgangerService.AltinnTilgang(
                            orgnr = overordnetEnhet,
                            altinn3Tilganger = emptySet(),
                            altinn2Tilganger = emptySet(),
                            underenheter = underenheterMedRettighet.map {
                                AltinnTilgangerService.AltinnTilgang(
                                    orgnr = it.orgnr,
                                    altinn3Tilganger = it.altinn3Rettigheter.toSet(),
                                    altinn2Tilganger = emptySet(),
                                    underenheter = emptyList(),
                                    navn = "NAVN TIL UNDERENHET",
                                    erSlettet = it.erSlettet,
                                    organisasjonsform = "BEDR",
                                )
                            },
                            navn = "NAVN TIL OVERORDNET ENHET",
                            erSlettet = erOverordnetEnhetSlettet,
                            organisasjonsform = "ORGL",
                        ),
                    ),
                    orgNrTilTilganger = underenheterMedRettighet.associate { it.orgnr to it.altinn3Rettigheter.toSet() },
                    tilgangTilOrgNr = underenheterMedRettighet.groupBySingleRettighet()
                        .mapValues { it.value.map { it.orgnr }.toSet() },
                    isError = false,
                ),
            )

        fun List<OrgnrMedEnkeltrettigheter>.groupBySingleRettighet(): Map<String, List<OrgnrMedEnkeltrettigheter>> =
            this.flatMap { orgnrMedRettighet ->
                orgnrMedRettighet.altinn3Rettigheter.map { rettighet ->
                    rettighet to orgnrMedRettighet
                }
            }.groupBy(keySelector = { it.first }, valueTransform = { it.second })
    }

    private val networkAlias = "altinn-tilganger-container"
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
            log.info("Startet (mock) altinnTilganger container for network '$network' og port '$port'")
        }

    fun envVars() =
        mapOf(
            "ALTINN_TILGANGER_PROXY_URL" to "http://$networkAlias:$port",
        )

    internal fun slettAlleRettigheter() = resetAllExpectations(client = mockServerClient!!)

    internal fun leggTilRettighet(
        orgnrTilOverordnetEnhet: String = ALTINN_OVERORDNET_ENHET,
        orgnrTilUnderenhet: String,
        altinn3RettighetForUnderenhet: String,
        erSlettet: Boolean = false,
    ) = leggTilRettigheter(
        orgnrTilOverordnetEnhet = orgnrTilOverordnetEnhet,
        underenheterMedRettighet = listOf(
            OrgnrMedEnkeltrettigheter(
                orgnr = orgnrTilUnderenhet,
                altinn3Rettigheter = listOf(altinn3RettighetForUnderenhet),
                erSlettet = false,
            ),
        ),
        erOverordnetEnhetSlettet = erSlettet,
    )

    fun leggTilRettigheter(
        orgnrTilOverordnetEnhet: String = ALTINN_OVERORDNET_ENHET,
        underenheterMedRettighet: List<OrgnrMedEnkeltrettigheter>,
        erOverordnetEnhetSlettet: Boolean = false,
    ) {
        log.info("Legger til rettigheter for underenheter '$underenheterMedRettighet'")

        runBlocking {
            mockServerClient!!.`when`(
                request()
                    .withMethod("POST")
                    .withPath("/altinn-tilganger"),
            ).respond(
                response().withBody(
                    lagJsonForAltinnTilgangerMock(
                        orgnrTilOverordnetEnhet,
                        underenheterMedRettighet,
                        erOverordnetEnhetSlettet,
                    ),
                ),
            )
        }
    }

    data class OrgnrMedEnkeltrettigheter(
        val orgnr: String,
        val altinn3Rettigheter: List<String>,
        val erSlettet: Boolean = false,
    )
}
