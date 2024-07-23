package no.nav.fia.arbeidsgiver.helper

import HEADER_SESJON_ID
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
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.DELTAKER_BASEPATH
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.BliMedDto
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.BliMedRequest
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.IdentifiserbartSpørsmålDto
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.DeltakerSpørsmålDto
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.SvarRequest
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.VERT_BASEPATH
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.TemaDto
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
    bliMedDTO: BliMedDto,
): IdentifiserbartSpørsmålDto {
    val response = performGet(
        url = "$DELTAKER_BASEPATH/${bliMedDTO.spørreundersøkelseId}",
    ) {
        header(HEADER_SESJON_ID, bliMedDTO.sesjonsId)
    }

    response.status shouldBe HttpStatusCode.OK

    return response.body()
}

internal suspend fun GenericContainer<*>.svarPåSpørsmål(
    spørsmål: IdentifiserbartSpørsmålDto,
    svarIder: List<String>,
    bliMedDTO: BliMedDto,
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
    spørsmål: IdentifiserbartSpørsmålDto,
    bliMedDTO: BliMedDto,
): DeltakerSpørsmålDto? {
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
    spørreundersøkelseId: UUID,
    token: String = TestContainerHelper.azureAccessToken().serialize(),
) {
    val response = performPost(
        url = "$VERT_BASEPATH/${spørreundersøkelseId}/tema/${temaId}/start",
        body = Unit
    ) {
        medAzureToken(token = token)
    }
    response.status shouldBe HttpStatusCode.OK
}

internal suspend fun GenericContainer<*>.hentAntallSvarForTema(
    temaId: Int,
    spørreundersøkelseId: UUID,
    token: String = TestContainerHelper.azureAccessToken().serialize(),
): Int {
    val response = performGet(
        url = "$VERT_BASEPATH/${spørreundersøkelseId}/tema/$temaId/antall-svar",
    ) {
        medAzureToken(token = token)
    }
    return response.body()
}

internal suspend fun GenericContainer<*>.hentAntallSvarForSpørreundersøkelse(
    spørreundersøkelseId: UUID,
    token: String = TestContainerHelper.azureAccessToken().serialize(),
): Int {
    val response = performGet(
        url = "$VERT_BASEPATH/${spørreundersøkelseId}/antall-fullfort",
    ) {
        medAzureToken(token = token)
    }
    return response.body()
}

internal suspend fun GenericContainer<*>.hentAntallSvarForSpørsmål(
    spørsmål: IdentifiserbartSpørsmålDto,
    spørreundersøkelseId: UUID,
    token: String = TestContainerHelper.azureAccessToken().serialize(),
): Int {
    val response = performGet(
        url = "$VERT_BASEPATH/${spørreundersøkelseId}/tema/${spørsmål.temaId}/sporsmal/${spørsmål.spørsmålId}/antall-svar",
    ) {
        medAzureToken(token = token)
    }
    return response.body()
}

internal suspend fun GenericContainer<*>.hentTemaDtoer(
    spørreundersøkelseId: UUID,
    token: String = TestContainerHelper.azureAccessToken().serialize(),
): List<TemaDto> {
    val response = performGet(
        url = "$VERT_BASEPATH/${spørreundersøkelseId}/oversikt",
    ) {
        medAzureToken(token = token)
    }
    return response.body()
}

internal suspend fun GenericContainer<*>.hentTemaDto(
    temaId: Int,
    spørreundersøkelseId: UUID,
    token: String = TestContainerHelper.azureAccessToken().serialize(),
): TemaDto {
    val response = performGet(
        url = "$VERT_BASEPATH/${spørreundersøkelseId}/tema/$temaId",
    ) {
        medAzureToken(token = token)
    }
    return response.body()
}

internal suspend fun GenericContainer<*>.hentResultater(
    spørreundersøkelseId: UUID,
    temaId: Int,
    token: String = TestContainerHelper.azureAccessToken().serialize(),
) = performGet(
    url = "$VERT_BASEPATH/$spørreundersøkelseId/tema/$temaId/resultater",
) {
    medAzureToken(token = token)
}

internal suspend fun GenericContainer<*>.stengTema(
    temaId: Int,
    spørreundersøkelseId: UUID,
    token: String = TestContainerHelper.azureAccessToken().serialize(),
) {
    val response = performPost(
        url = "$VERT_BASEPATH/${spørreundersøkelseId}/tema/$temaId/avslutt",
        body = Unit
    ) {
        medAzureToken(token = token)
    }

    response.status shouldBe HttpStatusCode.OK
}

internal suspend fun GenericContainer<*>.vertHenterVirksomhetsnavn(
    spørreundersøkelseId: UUID,
    token: String = TestContainerHelper.azureAccessToken().serialize(),
): String {
    val response = performGet(
        url = "$VERT_BASEPATH/$spørreundersøkelseId/virksomhetsnavn",
    ) {
        medAzureToken(token = token)
    }
    response.status shouldBe HttpStatusCode.OK

    return response.body()
}

internal suspend fun GenericContainer<*>.vertHenterAntallDeltakere(
    spørreundersøkelseId: UUID,
    token: String = TestContainerHelper.azureAccessToken().serialize(),
): Int {
    val response = performGet(
        url = "$VERT_BASEPATH/$spørreundersøkelseId/antall-deltakere",
    ) {
        medAzureToken(token = token)
    }

    response.status shouldBe HttpStatusCode.OK

    return response.body()
}

internal suspend fun GenericContainer<*>.bliMed(
    spørreundersøkelseId: UUID,
): BliMedDto {
    val response = performPost(
        url = BLI_MED_PATH,
        body = BliMedRequest(spørreundersøkelseId = spørreundersøkelseId.toString())
    )
    response.status shouldBe HttpStatusCode.OK
    val body = response.bodyAsText()
    return Json.decodeFromString<BliMedDto>(body)
}
