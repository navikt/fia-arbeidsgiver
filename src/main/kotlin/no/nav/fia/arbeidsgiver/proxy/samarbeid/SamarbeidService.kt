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
import kotlinx.serialization.json.Json
import no.nav.fia.arbeidsgiver.http.HttpClient.client
import no.nav.fia.arbeidsgiver.http.tokenx.TokenExchanger
import no.nav.fia.arbeidsgiver.konfigurasjon.Miljø.cluster
import no.nav.fia.arbeidsgiver.konfigurasjon.Miljø.fiaSamarbeidApiUrl
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class SamarbeidService {
    companion object {
        val json = Json { ignoreUnknownKeys = true }
        private val log: Logger = LoggerFactory.getLogger(this::class.java)
        val FIA_SAMARBEID_API = "$fiaSamarbeidApiUrl/api/arbeidsgiver/samarbeid"
    }

    suspend fun hentSamarbeidMedDokumenter(
        token: String,
        orgnr: String,
    ): List<SamarbeidMedDokumenterDto> =
        try {
            val client = getHttpClient(token = token)
            val response: HttpResponse = client.get {
                url("$FIA_SAMARBEID_API/$orgnr")
                accept(ContentType.Application.Json)
            }
            json.decodeFromString<List<SamarbeidMedDokumenterDto>>(response.body())
        } catch (e: Exception) {
            log.warn("Feil ved kall til Fia samarbeid api", e)
            emptyList()
        }

    private fun getHttpClient(token: String): HttpClient =
        client.config {
            install(Auth) {
                bearer {
                    loadTokens {
                        val exchangedToken = TokenExchanger.exchangeToken(
                            audience = "$cluster:pia:lydia-api",
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
