package no.nav.fia.arbeidsgiver.helper

import HEADER_SESJON_ID
import HEADER_VERT_ID
import io.kotest.assertions.fail
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.path
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.BLI_MED_PATH
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.NESTE_SPØRSMÅL_PATH
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.SPØRSMÅL_OG_SVAR_PATH
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.VERT_SPØRSMÅL_OG_SVAR_PATH
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.deltaker.DELTAKER_BASEPATH
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.BliMedDTO
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.BliMedRequest
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.DeltakerhandlingRequest
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.IdentifiserbartSpørsmål
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.NesteSpørsmålDTO
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.NySvarRequest
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.SpørsmålOgSvaralternativerTilFrontendDTO
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.SpørsmålsoversiktDto
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.VertshandlingRequest
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.vert.VERT_BASEPATH
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.vert.dto.TemaOversiktDto
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.Tema
import no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.dto.SpørreundersøkelseDto
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy
import org.testcontainers.images.builder.ImageFromDockerfile
import java.time.Duration
import java.util.*
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

private val httpClient = HttpClient(CIO) {
    install(ContentNegotiation) {
        json()
    }
}

private suspend fun GenericContainer<*>.performRequest(
    url: String,
    config: HttpRequestBuilder.() -> Unit = {},
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

internal suspend inline fun <reified T> GenericContainer<*>.performPost(
    url: String,
    body: T,
    crossinline config: HttpRequestBuilder.() -> Unit = {},
) =
    performRequest(url) {
        config()
        method = HttpMethod.Post
        header(HttpHeaders.ContentType, ContentType.Application.Json)
        setBody(body)
    }

internal suspend fun GenericContainer<*>.hentSpørsmål(
    tema: Tema,
    spørsmålId: String,
    bliMedDTO: BliMedDTO,
): SpørsmålOgSvaralternativerTilFrontendDTO {
    val response = performPost(
        url = "$SPØRSMÅL_OG_SVAR_PATH/$spørsmålId",
        body = DeltakerhandlingRequest(
            spørreundersøkelseId = bliMedDTO.spørreundersøkelseId,
            sesjonsId = bliMedDTO.sesjonsId,
            tema = tema,
        )
    )
    return response.body()
}

internal suspend fun GenericContainer<*>.hentFørsteSpørsmål(
    bliMedDTO: BliMedDTO,
): IdentifiserbartSpørsmål {
    val response = performGet(
        url = "$DELTAKER_BASEPATH/${bliMedDTO.spørreundersøkelseId}",
    ) {
        header(HEADER_SESJON_ID, bliMedDTO.sesjonsId)
    }

    response.status shouldBe HttpStatusCode.OK

    return response.body()
}

internal suspend fun GenericContainer<*>.svarPåSpørsmål(
    spørsmål: IdentifiserbartSpørsmål,
    svarId: String,
    bliMedDTO: BliMedDTO,
) {
    val response = performPost(
        url = "$DELTAKER_BASEPATH/${bliMedDTO.spørreundersøkelseId}/${spørsmål.tema}/${spørsmål.spørsmålId}/svar",
        body = NySvarRequest(svarId = svarId)
    ) {
        header(HEADER_SESJON_ID, bliMedDTO.sesjonsId)
    }

    response.status shouldBe HttpStatusCode.OK
}

internal suspend fun GenericContainer<*>.hentSpørsmålSomDeltaker(
    spørsmål: IdentifiserbartSpørsmål,
    bliMedDTO: BliMedDTO,
): SpørsmålsoversiktDto? {
    val response = performGet(
        url = "$DELTAKER_BASEPATH/${bliMedDTO.spørreundersøkelseId}/${spørsmål.tema}/${spørsmål.spørsmålId}",
    ) {
        header(HEADER_SESJON_ID, bliMedDTO.sesjonsId)
    }

    return when (response.status) {
        HttpStatusCode.OK -> response.body()
        HttpStatusCode.Accepted -> null
        else -> fail("Fikk feil status tilbake (${response.status})")
    }
}

internal suspend fun GenericContainer<*>.hentSpørsmålSomVertV2(
    spørsmål: IdentifiserbartSpørsmål,
    spørreundersøkelse: SpørreundersøkelseDto,
): SpørsmålsoversiktDto {
    val response = performGet(
        url = "$VERT_BASEPATH/${spørreundersøkelse.spørreundersøkelseId}/${spørsmål.tema}/${spørsmål.spørsmålId}",
    ) {
        header(HEADER_VERT_ID, spørreundersøkelse.vertId)
    }
    return response.body()
}

internal suspend fun GenericContainer<*>.hentTemaoversikt(
    spørreundersøkelse: SpørreundersøkelseDto,
): List<TemaOversiktDto> {
    val response = performGet(
        url = "$VERT_BASEPATH/${spørreundersøkelse.spørreundersøkelseId}",
    ) {
        header(HEADER_VERT_ID, spørreundersøkelse.vertId)
    }
    return response.body()
}

internal suspend fun GenericContainer<*>.hentSpørsmålSomVert(
    tema: Tema,
    spørsmålId: String,
    spørreundersøkelse: SpørreundersøkelseDto,
): SpørsmålOgSvaralternativerTilFrontendDTO {
    val response = performPost(
        url = "$VERT_SPØRSMÅL_OG_SVAR_PATH/$spørsmålId",
        body = VertshandlingRequest(
            spørreundersøkelseId = spørreundersøkelse.spørreundersøkelseId,
            vertId = spørreundersøkelse.vertId,
            tema = tema,
        )
    )
    return response.body()
}

internal suspend fun GenericContainer<*>.vertHenterAntallDeltakere(
    spørreundersøkelseId: String,
    vertId: String,
): Int {
    val response = performGet(
        url = "$VERT_BASEPATH/$spørreundersøkelseId/antall-deltakere",
    ) {
        header(HEADER_VERT_ID, vertId)
    }

    response.status shouldBe HttpStatusCode.OK

    return response.body()
}

internal suspend fun GenericContainer<*>.bliMed(
    spørreundersøkelseId: UUID,
): BliMedDTO {
    val response = performPost(
        url = BLI_MED_PATH,
        body = BliMedRequest(spørreundersøkelseId = spørreundersøkelseId.toString())
    )
    response.status shouldBe HttpStatusCode.OK
    val body = response.bodyAsText()
    return Json.decodeFromString<BliMedDTO>(body)
}

internal suspend fun GenericContainer<*>.nesteSpørsmål(
    bliMedDTO: BliMedDTO,
    nåværendeSpørsmålId: String,
) =
    performPost(
        url = "$NESTE_SPØRSMÅL_PATH/$nåværendeSpørsmålId",
        body = DeltakerhandlingRequest(
            spørreundersøkelseId = bliMedDTO.spørreundersøkelseId,
            sesjonsId = bliMedDTO.sesjonsId,
        )
    ).body<NesteSpørsmålDTO>()
