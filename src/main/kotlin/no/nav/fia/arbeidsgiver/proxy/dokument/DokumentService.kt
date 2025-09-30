package no.nav.fia.arbeidsgiver.proxy.dokument

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import no.nav.fia.arbeidsgiver.http.HttpClient.client
import no.nav.fia.arbeidsgiver.http.tokenx.TokenExchanger
import no.nav.fia.arbeidsgiver.konfigurasjon.Miljø.cluster
import no.nav.fia.arbeidsgiver.konfigurasjon.Miljø.fiaDokumentPubliseringUrl
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.UUID

class DokumentService {
    companion object {
        val json = Json { ignoreUnknownKeys = true }
        private val log: Logger = LoggerFactory.getLogger(this::class.java)
        val FIA_DOKUMENT_PUBLISERING_API = "$fiaDokumentPubliseringUrl/dokument"
    }

    suspend fun hentDokument(
        token: String,
        orgnr: String,
        dokumentId: UUID,
    ): DokumentDto? =
        try {
            val client = getHttpClient(token = token)
            val response: HttpResponse = client.get {
                url("$FIA_DOKUMENT_PUBLISERING_API/orgnr/$orgnr/dokumentId/$dokumentId")
                accept(ContentType.Application.Json)
            }
            if (response.status == HttpStatusCode.OK) {
                json.decodeFromString<DokumentDto>(response.body())
            } else {
                log.warn("Kunne ikke hente dokument fra Fia dokument publisering, ${response.status}")
                null
            }
        } catch (e: Exception) {
            log.warn("Feil ved kall til Fia dokument publisering", e)
            null
        }

    private fun getHttpClient(token: String): HttpClient =
        client.config {
            install(Auth) {
                bearer {
                    loadTokens {
                        val exchangedToken = TokenExchanger.exchangeToken(
                            audience = "$cluster:pia:fia-dokument-publisering",
                            subjectToken = token,
                        )
                        BearerTokens(
                            accessToken = exchangedToken,
                            refreshToken = exchangedToken,
                        )
                    }
                }
            }
        }

    @Serializable
    data class DokumentDto(
        val dokumentId: String,
        val type: String,
        val samarbeidNavn: String,
        val innhold: JsonObject,
    )
}
