package no.nav.fia.arbeidsgiver.samarbeidsstatus.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.request.accept
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import no.nav.fia.arbeidsgiver.http.HttpClient.client
import no.nav.fia.arbeidsgiver.http.tokenx.TokenExchanger
import no.nav.fia.arbeidsgiver.konfigurasjon.Miljø.altinnTilgangerProxyUrl
import no.nav.fia.arbeidsgiver.konfigurasjon.Miljø.cluster
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class AltinnTilgangerService {
    private val altinnTilgangerUrl: String = "$altinnTilgangerProxyUrl/altinn-tilganger"
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    companion object {
        const val ENKELRETTIGHET_FOREBYGGE_FRAVÆR_I_ALTINN = "5934:1"

        fun AltinnTilganger?.harEnkeltTilgang(
            orgnr: String?,
            altinn2Tilgang: String = ENKELRETTIGHET_FOREBYGGE_FRAVÆR_I_ALTINN,
        ) = this?.orgNrTilTilganger?.get(orgnr)?.contains(altinn2Tilgang) ?: false

        fun AltinnTilganger?.harTilgangTilOrgnr(orgnr: String?): Boolean =
            this?.virksomheterVedkommendeHarTilgangTil()?.contains(orgnr) ?: false

        private fun AltinnTilganger?.virksomheterVedkommendeHarTilgangTil(): List<String> =
            this?.hierarki?.flatMap {
                flatten(it) { o -> o.orgnr }
            }?.toList() ?: emptyList()

        private fun <T> flatten(
            altinnTilgang: AltinnTilgang,
            mapFn: (AltinnTilgang) -> T,
        ): Set<T> = setOf(mapFn(altinnTilgang)) + altinnTilgang.underenheter.flatMap { flatten(it, mapFn) }
    }

    private fun getHttpClient(token: String): HttpClient =
        client.config {
            install(Auth) {
                bearer {
                    loadTokens {
                        BearerTokens(
                            accessToken = TokenExchanger.exchangeToken(
                                token = token,
                                audience = "$cluster:fager:arbeidsgiver-altinn-tilganger",
                            ),
                            refreshToken = TokenExchanger.exchangeToken(
                                token = token,
                                audience = "$cluster:fager:arbeidsgiver-altinn-tilganger",
                            ),
                        )
                    }
                }
            }
        }

    suspend fun hentAltinnTilganger(token: String): AltinnTilganger? =
        try {
            logger.info("henter Altinn tilganger på URL $altinnTilgangerUrl")
            val client = getHttpClient(token)
            val response: HttpResponse = client.post {
                url(altinnTilgangerUrl)
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
                setBody("{}")
            }
            Json.decodeFromString<AltinnTilganger>(response.body())
        } catch (e: Exception) {
            logger.error("Feil ved kall til Altinn tilganger", e)
            null
        }

    @Serializable
    data class AltinnTilgang(
        val orgnr: String,
        val altinn3Tilganger: Set<String>,
        val altinn2Tilganger: Set<String>,
        val underenheter: List<AltinnTilgang>,
        val navn: String,
        val organisasjonsform: String,
    )

    @Serializable
    data class AltinnTilganger(
        val hierarki: List<AltinnTilgang>,
        val orgNrTilTilganger: Map<String, Set<String>>,
        val tilgangTilOrgNr: Map<String, Set<String>>,
        val isError: Boolean,
    )
}
