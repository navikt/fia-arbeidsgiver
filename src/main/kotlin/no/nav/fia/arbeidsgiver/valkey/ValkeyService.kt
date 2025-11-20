package no.nav.fia.arbeidsgiver.valkey

import io.valkey.JedisPool

class ValkeyService(
    val jedisPool: JedisPool,
) {
    val defaultTimeToLiveSeconds: Long

    companion object {
        const val ETT_DØGN = 1 * 24 * 60 * 60L
    }

    init {
        defaultTimeToLiveSeconds = ETT_DØGN
    }

    fun slett(
        type: Type,
        nøkkel: String,
    ) = jedisPool.resource.use { jedis -> jedis.del("${type.name}-$nøkkel") }

    fun lagre(
        type: Type,
        nøkkel: String,
        verdi: String,
        ttl: Long = defaultTimeToLiveSeconds,
    ) {
        jedisPool.resource.use { jedis -> jedis.setex("${type.name}-$nøkkel", ttl, verdi) }
    }

    fun hente(
        type: Type,
        nøkkel: String,
    ): String? = jedisPool.resource.use { jedis -> jedis.get("${type.name}-$nøkkel") }
}

enum class Type {
    SPØRREUNDERSØKELSE_RESULTAT,
    SPØRREUNDERSØKELSE,
    SESJON,
    ANTALL_DELTAKERE,
    ANTALL_SVAR_FOR_SPØRSMÅL,
    TEMA_STATUS,
    ER_SPØRSMÅL_ÅPENT,
    ER_TEMA_ÅPENT,
}
