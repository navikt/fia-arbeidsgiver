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
import java.time.Duration
import java.util.*
import kotlin.io.path.Path
import kotlinx.serialization.json.Json
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.BLI_MED_PATH
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.deltaker.DELTAKER_BASEPATH
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.BliMedDTO
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.BliMedRequest
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.IdentifiserbartSpørsmål
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.SpørsmålsoversiktDto
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.SvarRequest
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.vert.VERT_BASEPATH
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.vert.dto.TemaOversiktDto
import no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.dto.SpørreundersøkelseDto
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy
import org.testcontainers.images.builder.ImageFromDockerfile

class TestContainerHelper {
    companion object {
        val log: Logger = LoggerFactory.getLogger(TestContainerHelper::class.java)
        val network = Network.newNetwork()

        val authServer = AuthContainer(network)
        val kafka = KafkaContainer(network)
        val redis = RedisContainer(network)
        val altinnProxy = AltinnProxyContainer()

        const val VERT_NAV_IDENT = "Z12345"

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

        internal fun tokenXAccessToken(
            subject: String = "123",
            audience: String = "tokenx:fia-arbeidsgiver",
            claims: Map<String, String> = mapOf(
                "acr" to "Level4",
                "pid" to subject,
            ),
        ) = authServer.issueToken(
            subject = subject,
            audience = audience,
            claims = claims,
            issuerId = "tokenx"
        )

        internal fun azureAccessToken(
            subject: String = "123",
            audience: String = "azure:fia-arbeidsgiver",
            claims: Map<String, Any> = mapOf(
                "NAVident" to VERT_NAV_IDENT,
                "groups" to listOf(AuthContainer.saksbehandlerGroupId)
            ),
        ) = authServer.issueToken(
            subject = subject,
            audience = audience,
            claims = claims,
            issuerId = "azure"
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

internal fun withTokenXToken(): HttpRequestBuilder.() -> Unit = {
    header(HttpHeaders.Authorization, "Bearer ${TestContainerHelper.tokenXAccessToken().serialize()}")
}

internal fun HttpRequestBuilder.medAzureToken(
    token: String = TestContainerHelper.azureAccessToken().serialize(),
) {
    header(HttpHeaders.Authorization, "Bearer $token")
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
    svarIder: List<String>,
    bliMedDTO: BliMedDTO,
    block: () -> Unit = {},
) {
    val response = performPost(
        url = "$DELTAKER_BASEPATH/${bliMedDTO.spørreundersøkelseId}/tema/${spørsmål.temaId}/sporsmal/${spørsmål.spørsmålId}/svar",
        body = SvarRequest(svarIder = svarIder)
    ) {
        header(HEADER_SESJON_ID, bliMedDTO.sesjonsId)
    }

    when (response.status) {
        HttpStatusCode.OK, HttpStatusCode.SeeOther -> Unit
        else -> fail("Fikk feil status tilbake (${response.status})")
    }

    block()
}

internal suspend fun GenericContainer<*>.hentSpørsmålSomDeltaker(
    spørsmål: IdentifiserbartSpørsmål,
    bliMedDTO: BliMedDTO,
): SpørsmålsoversiktDto? {
    val response = performGet(
        url = "$DELTAKER_BASEPATH/${bliMedDTO.spørreundersøkelseId}/tema/${spørsmål.temaId}/sporsmal/${spørsmål.spørsmålId}",
    ) {
        header(HEADER_SESJON_ID, bliMedDTO.sesjonsId)
    }

    return when (response.status) {
        HttpStatusCode.OK -> response.body()
        HttpStatusCode.Accepted -> null
        else -> fail("Fikk feil status tilbake (${response.status})")
    }
}

internal suspend fun GenericContainer<*>.åpneTema(
    temaId: Int,
    spørreundersøkelse: SpørreundersøkelseDto,
    token: String = TestContainerHelper.azureAccessToken().serialize(),
) {
    val response = performPost(
        url = "$VERT_BASEPATH/${spørreundersøkelse.spørreundersøkelseId}/tema/${temaId}/start",
        body = Unit
    ) {
        header(HEADER_VERT_ID, spørreundersøkelse.vertId)
        medAzureToken(token = token)
    }
    response.status shouldBe HttpStatusCode.OK
}

internal suspend fun GenericContainer<*>.hentSpørsmålSomVert(
    spørsmål: IdentifiserbartSpørsmål,
    spørreundersøkelse: SpørreundersøkelseDto,
    token: String = TestContainerHelper.azureAccessToken().serialize(),
): SpørsmålsoversiktDto {
    val response = performGet(
        url = "$VERT_BASEPATH/${spørreundersøkelse.spørreundersøkelseId}/tema/${spørsmål.temaId}/sporsmal/${spørsmål.spørsmålId}",
    ) {
        header(HEADER_VERT_ID, spørreundersøkelse.vertId)
        medAzureToken(token = token)
    }
    return response.body()
}

internal suspend fun GenericContainer<*>.hentAntallSvarForTema(
    temaId: Int,
    spørreundersøkelse: SpørreundersøkelseDto,
    token: String = TestContainerHelper.azureAccessToken().serialize(),
): Int {
    val response = performGet(
        url = "$VERT_BASEPATH/${spørreundersøkelse.spørreundersøkelseId}/tema/$temaId/antall-svar",
    ) {
        header(HEADER_VERT_ID, spørreundersøkelse.vertId)
        medAzureToken(token = token)
    }
    return response.body()
}

internal suspend fun GenericContainer<*>.hentAntallSvarForSpørreundersøkelse(
    spørreundersøkelse: SpørreundersøkelseDto,
    token: String = TestContainerHelper.azureAccessToken().serialize(),
): Int {
    val response = performGet(
        url = "$VERT_BASEPATH/${spørreundersøkelse.spørreundersøkelseId}/antall-fullfort",
    ) {
        header(HEADER_VERT_ID, spørreundersøkelse.vertId)
        medAzureToken(token = token)
    }
    return response.body()
}

internal suspend fun GenericContainer<*>.hentAntallSvarForSpørsmål(
    spørsmål: IdentifiserbartSpørsmål,
    spørreundersøkelse: SpørreundersøkelseDto,
    token: String = TestContainerHelper.azureAccessToken().serialize(),
): Int {
    val response = performGet(
        url = "$VERT_BASEPATH/${spørreundersøkelse.spørreundersøkelseId}/tema/${spørsmål.temaId}/sporsmal/${spørsmål.spørsmålId}/antall-svar",
    ) {
        header(HEADER_VERT_ID, spørreundersøkelse.vertId)
        medAzureToken(token = token)
    }
    return response.body()
}

internal suspend fun GenericContainer<*>.hentTemaoversikt(
    spørreundersøkelse: SpørreundersøkelseDto,
    token: String = TestContainerHelper.azureAccessToken().serialize(),
): List<TemaOversiktDto> {
    val response = performGet(
        url = "$VERT_BASEPATH/${spørreundersøkelse.spørreundersøkelseId}",
    ) {
        header(HEADER_VERT_ID, spørreundersøkelse.vertId)
        medAzureToken(token = token)
    }
    return response.body()
}

internal suspend fun GenericContainer<*>.hentTemaoversiktForEttTema(
    spørreundersøkelse: SpørreundersøkelseDto,
    temaId: Int,
    token: String = TestContainerHelper.azureAccessToken().serialize(),
): TemaOversiktDto {
    val response = performGet(
        url = "$VERT_BASEPATH/${spørreundersøkelse.spørreundersøkelseId}/tema/$temaId",
    ) {
        header(HEADER_VERT_ID, spørreundersøkelse.vertId)
        medAzureToken(token = token)
    }
    return response.body()
}

internal suspend fun GenericContainer<*>.hentResultater(
    spørreundersøkelse: SpørreundersøkelseDto,
    temaId: Int,
    token: String = TestContainerHelper.azureAccessToken().serialize(),
) = performGet(
    url = "$VERT_BASEPATH/${spørreundersøkelse.spørreundersøkelseId}/tema/$temaId/resultater",
) {
    header(HEADER_VERT_ID, spørreundersøkelse.vertId)
    medAzureToken(token = token)
}

internal suspend fun GenericContainer<*>.stengTema(
    temaId: Int,
    spørreundersøkelse: SpørreundersøkelseDto,
    token: String = TestContainerHelper.azureAccessToken().serialize(),
) {
    val response = performPost(
        url = "$VERT_BASEPATH/${spørreundersøkelse.spørreundersøkelseId}/tema/$temaId/avslutt",
        body = Unit
    ) {
        header(HEADER_VERT_ID, spørreundersøkelse.vertId)
        medAzureToken(token = token)
    }

    response.status shouldBe HttpStatusCode.OK
}

internal suspend fun GenericContainer<*>.vertHenterVirksomhetsnavn(
    spørreundersøkelseId: String,
    vertId: String,
    token: String = TestContainerHelper.azureAccessToken().serialize(),
): String {
    val response = performGet(
        url = "$VERT_BASEPATH/$spørreundersøkelseId/virksomhetsnavn",
    ) {
        header(HEADER_VERT_ID, vertId)
        medAzureToken(token = token)
    }
    response.status shouldBe HttpStatusCode.OK

    return response.body()
}

internal suspend fun GenericContainer<*>.vertHenterAntallDeltakere(
    spørreundersøkelseId: String,
    vertId: String,
    token: String = TestContainerHelper.azureAccessToken().serialize(),
): Int {
    val response = performGet(
        url = "$VERT_BASEPATH/$spørreundersøkelseId/antall-deltakere",
    ) {
        header(HEADER_VERT_ID, vertId)
        medAzureToken(token = token)
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
