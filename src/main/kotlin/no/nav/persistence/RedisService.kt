package no.nav.persistence

import io.ktor.http.*
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.StaticCredentialsProvider
import io.lettuce.core.api.sync.RedisCommands
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import no.nav.api.Feil
import no.nav.domene.samarbeidsstatus.IASakStatus
import no.nav.domene.sporreundersokelse.Spørreundersøkelse
import no.nav.domene.sporreundersokelse.SpørreundersøkelseStatus
import no.nav.konfigurasjon.Redis
import java.util.*


class RedisService(
    url: String = Redis.redisUrl,
    username: String = Redis.redisUsername,
    password: String? = Redis.redisPassword
) {
    val redisUri: RedisURI
    val sync: RedisCommands<String, String>
    val defaultTimeToLiveSeconds: Long

    init {
        redisUri = RedisURI.create(url)
        redisUri.credentialsProvider = StaticCredentialsProvider(username, password?.toCharArray())

        val redisClient = RedisClient.create(redisUri)
        val connection = redisClient.connect()
        sync = connection.sync()
        defaultTimeToLiveSeconds = 2 * 365 * 24 * 60 * 60L // To år!
    }

    fun lagre(iaSakStatus: IASakStatus) {
        val gammelStatus = henteSakStatus(iaSakStatus.orgnr)
        if (gammelStatus == null || gammelStatus.sistOppdatert <= iaSakStatus.sistOppdatert)
            lagre(Type.SAMARBEIDSSTATUS, iaSakStatus.orgnr, Json.encodeToString(iaSakStatus))
    }

    fun lagre(spørreundersøkelse: Spørreundersøkelse) {
        lagre(Type.SPØRREUNDERSØKELSE, spørreundersøkelse.spørreundersøkelseId.toString(), Json.encodeToString(spørreundersøkelse))
    }

    fun lagreSesjon(sesjonsId: UUID, spørreundersøkelseId: UUID) {
        lagre(Type.SESJON, sesjonsId.toString(), spørreundersøkelseId.toString())
    }

    fun henteSakStatus(orgnr: String): IASakStatus? {
        return hente(Type.SAMARBEIDSSTATUS, orgnr)?.let {
            Json.decodeFromString(it)
        }
    }

    fun hentePågåendeSpørreundersøkelse(id: UUID): Spørreundersøkelse {
        val undersøkelse = hente(Type.SPØRREUNDERSØKELSE, id.toString())?.let {
            Json.decodeFromString<Spørreundersøkelse>(it)
        } ?: throw Feil(feilmelding = "Ukjent spørreundersøkelse '$id'", feilkode = HttpStatusCode.Forbidden)

        return if (undersøkelse.status == SpørreundersøkelseStatus.OPPRETTET) {
            undersøkelse
        } else throw Feil(feilmelding = "Avsluttet spørreundersøkelse '$id'", feilkode = HttpStatusCode.Gone)
    }

    fun henteSpørreundersøkelseIdFraSesjon(sesjonsId: UUID): UUID? {
        return hente(Type.SESJON, sesjonsId.toString())?.let {
            UUID.fromString(it)
        }
    }

    private fun lagre(
        type: Type,
        nøkkel: String,
        verdi: String,
        ttl: Long = defaultTimeToLiveSeconds,
    ) {
        sync.setex("${type.name}-$nøkkel", ttl, verdi)
    }

    private fun hente(
        type: Type,
        nøkkel: String
    ): String? {
        return sync.get("${type.name}-$nøkkel")
    }
}

enum class Type {
    SAMARBEIDSSTATUS, SPØRREUNDERSØKELSE, SESJON
}