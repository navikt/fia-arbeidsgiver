package no.nav.fia.arbeidsgiver.redis

import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.StaticCredentialsProvider
import io.lettuce.core.api.sync.RedisCommands
import no.nav.fia.arbeidsgiver.konfigurasjon.Redis


class RedisService(
    url: String = Redis.redisUrl,
    username: String = Redis.redisUsername,
    password: String? = Redis.redisPassword
) {
    val redisUri: RedisURI = RedisURI.create(url)
    val sync: RedisCommands<String, String>
    val defaultTimeToLiveSeconds: Long

    init {
        redisUri.credentialsProvider = StaticCredentialsProvider(username, password?.toCharArray())

        val redisClient = RedisClient.create(redisUri)
        val connection = redisClient.connect()
        sync = connection.sync()

        val TWO_YEARS = 2 * 365 * 24 * 60 * 60L
        defaultTimeToLiveSeconds = TWO_YEARS
    }

    fun slett(
        type: Type,
        nøkkel: String,
    ) = sync.del("${type.name}-$nøkkel")

    fun lagre(
        type: Type,
        nøkkel: String,
        verdi: String,
        ttl: Long = defaultTimeToLiveSeconds,
    ) {
        sync.setex("${type.name}-$nøkkel", ttl, verdi)
    }

    fun hente(
        type: Type,
        nøkkel: String
    ): String? {
        return sync.get("${type.name}-$nøkkel")
    }
}

enum class Type {
    SAMARBEIDSSTATUS,
    SPØRREUNDERSØKELSE,
    SESJON,
    ANTALL_DELTAKERE,
    TEMASTATUS
}