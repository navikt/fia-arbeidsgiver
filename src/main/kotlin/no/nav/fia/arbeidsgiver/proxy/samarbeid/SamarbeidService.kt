package no.nav.fia.arbeidsgiver.proxy.samarbeid

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
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import no.nav.fia.arbeidsgiver.http.HttpClient.client
import no.nav.fia.arbeidsgiver.http.tokenx.TokenExchanger
import no.nav.fia.arbeidsgiver.konfigurasjon.Miljø.cluster
import no.nav.fia.arbeidsgiver.konfigurasjon.Miljø.fiaSamarbeidApiUrl
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class SamarbeidService {
    companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java)
        val FIA_SAMARBEID_API = "$fiaSamarbeidApiUrl/api/arbeidsgiver/samarbeid"
    }

    suspend fun hentSamarbeid(token: String, orgnr: String): List<IASamarbeidDto> =
        try {
            val client = getHttpClient(token = token)
            val response: HttpResponse = client.get {
                url("$FIA_SAMARBEID_API/$orgnr")
                accept(ContentType.Application.Json)
            }
            val jsonParser = Json { ignoreUnknownKeys = true }
            jsonParser.decodeFromString<List<IASamarbeidDto>>(response.body())
        } catch (e: Exception) {
            log.warn("Feil ved kall til Fia samarbeid api", e)
            emptyList()
        }

    private fun getHttpClient(token: String): HttpClient {
        return client.config {
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
    }


    @Serializable
    data class IASamarbeidDto(
        val id: Int,
        val saksnummer: String,
        val navn: String,
        val status: Status?,
        val opprettet: LocalDateTime,
        val avbrutt: LocalDateTime? = null,
        val fullført: LocalDateTime? = null,
        val sistEndret: LocalDateTime? = null,
    ) {
        enum class Status {
            AKTIV,
            FULLFØRT,
            SLETTET,
            AVBRUTT,
        }
    }
}
