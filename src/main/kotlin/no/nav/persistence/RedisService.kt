package no.nav.persistence

import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.api.sync.RedisCommands
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import no.nav.kafka.IASakStatus
import no.nav.konfigurasjon.Redis


class RedisService(host: String = Redis.redisHost, port: Int = Redis.redisPort, password: String = Redis.redisPassword) {
    val redisUri: RedisURI
    val sync: RedisCommands<String, String>
    val defaultTimeToLiveSeconds: Long

    init {
        redisUri = RedisURI.Builder.redis(host, port)
            .withPassword(password as CharSequence)
            .build()
        val redisClient = RedisClient.create(redisUri)
        val connection = redisClient.connect()
        sync = connection.sync()
        defaultTimeToLiveSeconds = 2 * 365 * 24 * 60 * 60L // To år!
    }

    fun lagre(iaSakStatus: IASakStatus) {
        lagre(iaSakStatus.orgnr, Json.encodeToString(iaSakStatus))
    }

    fun henteSakStatus(orgnr: String): IASakStatus {
        return Json.decodeFromString(hente(orgnr))
    }


    private fun lagre(nøkkel: String, verdi: String, ttl: Long = defaultTimeToLiveSeconds) {
        sync.setex(nøkkel, ttl, verdi)
    }

    private fun hente(nøkkel: String): String {
        return sync.get(nøkkel) ?: throw RuntimeException("Finner ikke verdi for nøkkel: $nøkkel")
    }
}
