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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import no.nav.fia.arbeidsgiver.http.HttpClient.client
import no.nav.fia.arbeidsgiver.http.tokenx.TokenExchanger
import no.nav.fia.arbeidsgiver.konfigurasjon.Miljø.cluster
import no.nav.fia.arbeidsgiver.konfigurasjon.Miljø.fiaDokumentPubliseringUrl
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class DokumentService {
    companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java)
        val FIA_ARBEIDSGIVER_DOKUMENT_API = "$fiaDokumentPubliseringUrl/dokument"
    }

    suspend fun hentDokumenter(token: String, orgnr: String): List<DokumentDto> =
        try {
            log.debug("henter dokumenter på URL $FIA_ARBEIDSGIVER_DOKUMENT_API")
            val client = getHttpClient(token = token, orgnr = orgnr)
            val response: HttpResponse = client.get {
                url("$FIA_ARBEIDSGIVER_DOKUMENT_API/$orgnr")
                accept(ContentType.Application.Json)
            }
            val jsonParser = Json { ignoreUnknownKeys = true }
            jsonParser.decodeFromString<List<DokumentDto>>(response.body())
        } catch (e: Exception) {
            log.warn("Feil ved kall til Fia dokument publisering", e)
            emptyList()
        }

    private fun getHttpClient(token: String, orgnr: String): HttpClient {
        return client.config {
            install(Auth) {
                bearer {
                    loadTokens {
                        //val exchangedToken = TokenExchanger.exchangeMedSelfIssuedToken(
                        val exchangedToken = TokenExchanger.exchangeMedOpenIdToken(
                            audience = "$cluster:pia:fia-dokument-publisering",
                            openIdToken = token,
                            //scope = Scope.DOKUMENT_LESETILGANG,
                            //orgnr = orgnr,
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
    data class DokumentDto(
        val dokumentId: String,
        val type: String,
        val samarbeidNavn: String,
        val innhold: String,
    )
}
