package no.nav.fia.arbeidsgiver.helper

import no.nav.fia.arbeidsgiver.persistence.RedisService
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.utility.DockerImageName

private const val REDIS_PORT = 6379

class RedisContainer(network: Network) {
    val networkAlias = "redisContainer"
    val redisUsername = "redislokaltusername"
    val redisPassord = "redislokaltpassord"

    fun getEnv() = mapOf(
        "REDIS_URI_FIA_SAMARBEIDSSTATUS" to "redis://$networkAlias:$REDIS_PORT",
        "REDIS_USERNAME_FIA_SAMARBEIDSSTATUS" to redisUsername,
        "REDIS_PASSWORD_FIA_SAMARBEIDSSTATUS" to redisPassord
    )

    val redisService
        get() = RedisService(
            url = "redis://${TestContainerHelper.redis.container.host}:${TestContainerHelper.redis.container.firstMappedPort}",
            username = redisUsername,
            password = redisPassord,
        )

    val container = GenericContainer(
        DockerImageName.parse("redis:6.2.12-alpine")
    )
        .withNetwork(network)
        .withNetworkAliases(networkAlias)
        .withLogConsumer(Slf4jLogConsumer(TestContainerHelper.log).withPrefix(networkAlias).withSeparateOutputStreams())
        .withExposedPorts(REDIS_PORT)
        .withCommand("redis-server --user $redisUsername on +@all ~* >$redisPassord")
        .apply {
            start()
        }
}
