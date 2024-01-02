package no.nav.persistence

import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.StaticCredentialsProvider
import io.lettuce.core.api.sync.RedisCommands
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import no.nav.kafka.IASakStatus
import no.nav.konfigurasjon.Redis


class RedisService(url: String = Redis.redisUrl, username: String = Redis.redisUsername, password: String? = Redis.redisPassword) {
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
            lagre(iaSakStatus.orgnr, Json.encodeToString(iaSakStatus))
    }

    fun henteSakStatus(orgnr: String): IASakStatus? {
        return hente(orgnr)?.let {
            Json.decodeFromString(it)
        }
    }


    private fun lagre(nøkkel: String, verdi: String, ttl: Long = defaultTimeToLiveSeconds) {
        sync.setex(nøkkel, ttl, verdi)
    }

    private fun hente(nøkkel: String): String? {
        return sync.get(nøkkel)
    }
}
