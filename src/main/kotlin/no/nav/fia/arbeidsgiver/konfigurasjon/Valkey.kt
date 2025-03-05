package no.nav.fia.arbeidsgiver.konfigurasjon

import io.valkey.DefaultJedisClientConfig
import io.valkey.HostAndPort
import io.valkey.JedisPool
import io.valkey.JedisPoolConfig

object Valkey {
    val host: String by lazy { System.getenv("VALKEY_HOST_FIA_SAMARBEIDSSTATUS") }
    val port: Int by lazy { System.getenv("VALKEY_PORT_FIA_SAMARBEIDSSTATUS").toInt() }
    val username: String by lazy { System.getenv("VALKEY_USERNAME_FIA_SAMARBEIDSSTATUS") }
    val password: String by lazy { System.getenv("VALKEY_PASSWORD_FIA_SAMARBEIDSSTATUS") }
}

fun jedisPool(
    host: String = Valkey.host,
    port: Int = Valkey.port,
    username: String = Valkey.username,
    password: String = Valkey.password,
    ssl: Boolean = Miljø.cluster != Cluster.lokal,
) = JedisPool(
    JedisPoolConfig(),
    HostAndPort(host, port),
    DefaultJedisClientConfig.builder()
        .ssl(ssl)
        .user(username)
        .password(password)
        .build(),
)
