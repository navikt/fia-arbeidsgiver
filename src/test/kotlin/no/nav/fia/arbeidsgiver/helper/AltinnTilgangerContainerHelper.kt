package no.nav.fia.arbeidsgiver.helper

import kotlinx.coroutines.runBlocking
import no.nav.fia.arbeidsgiver.helper.TestContainerHelper.Companion.log
import org.mockserver.client.MockServerClient
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName

class AltinnTilgangerContainerHelper(
    network: Network = Network.newNetwork(),
    logger: Logger = LoggerFactory.getLogger(AltinnTilgangerContainerHelper::class.java),
) {
    companion object {
        const val ALTINN_ORGNR_1 = "311111111"
        const val ALTINN_ORGNR_2 = "322222222"
        const val ORGNR_UTEN_TILKNYTNING = "300000000"
    }

    private val networkAlias = "mockAltinnTilgangerContainer"
    private val port = 7070

    private val dockerImageName = DockerImageName.parse("mockserver/mockserver")
    val container: GenericContainer<*> = GenericContainer(dockerImageName)
        .withNetwork(network)
        .withNetworkAliases(networkAlias)
        .withExposedPorts(port)
        .withLogConsumer(Slf4jLogConsumer(logger).withPrefix(networkAlias).withSeparateOutputStreams())
        .withEnv(
            mapOf(
                "MOCKSERVER_LIVENESS_HTTP_GET_PATH" to "/isRunning",
                "SERVER_PORT" to "7070",
                "TZ" to "Europe/Oslo",
            ),
        )
        .waitingFor(Wait.forHttp("/isRunning").forStatusCode(200))
        .apply {
            start()
        }.also {
            logger.info("Startet (mock) altinnTilganger container for network '$network' og port '$port'")
        }

    fun envVars() =
        mapOf(
            "ALTINN_TILGANGER_PROXY_URL" to "http://$networkAlias:$port",
        )

    internal fun slettAlleRettigheter() {
        val client = MockServerClient(
            container.host,
            container.getMappedPort(7070),
        )
        client.reset()
    }

    internal fun leggTilRettigheter(
        overordnetEnhet: String = "400000000",
        underenhet: String,
        altinn2Rettighet: String = "",
        altinn3Rettighet: String = "nav-ia-sykefravarsstatistikk-IKKE-SATT-OPP-ENDA",
    ) {
        log.debug(
            "Oppretter MockServerClient med host '${container.host}' og port '${
                container.getMappedPort(
                    7070,
                )
            }'. Legger til rettighet '$altinn2Rettighet' for underenhet '$underenhet'",
        )
        val client = MockServerClient(
            container.host,
            container.getMappedPort(7070),
        )
        runBlocking {
            client.`when`(
                request()
                    .withMethod("POST")
                    .withPath("/altinn-tilganger"),
            ).respond(
                response().withBody(
                    """
                    {
                      "hierarki": [
                        {
                          "orgnr": "$overordnetEnhet",
                          "altinn3Tilganger": [],
                          "altinn2Tilganger": [],
                          "underenheter": [
                            {
                              "orgnr": "$underenhet",
                              "altinn3Tilganger": [
                                "$altinn3Rettighet"
                              ],
                              "altinn2Tilganger": [
                                "$altinn2Rettighet"
                              ],
                              "underenheter": [],
                              "navn": "NAVN TIL UNDERENHET",
                              "organisasjonsform": "BEDR"
                            }
                          ],
                          "navn": "NAVN TIL OVERORDNET ENHET",
                          "organisasjonsform": "ORGL"
                        }
                      ],
                      "orgNrTilTilganger": {
                        "$underenhet": [
                          "$altinn3Rettighet",
                          "$altinn2Rettighet"
                        ]
                      },
                      "tilgangTilOrgNr": {
                        "$altinn3Rettighet": [
                          "$underenhet"
                        ],
                        "$altinn2Rettighet": [
                          "$underenhet"
                        ]
                      },
                      "isError": false
                    }
                    """.trimIndent(),
                ),
            )
        }
    }
}
