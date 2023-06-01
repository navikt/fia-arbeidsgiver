package no.nav.helper

import no.nav.persistence.RedisService
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.utility.DockerImageName

private const val REDIS_PORT = 6379

class RedisContainer(network: Network) {
    val networkAlias = "redisContainer"
    val redisPassord = "redislokaltpassord"

    fun getEnv() = mapOf(
        "REDIS_HOST" to networkAlias,
        "REDIS_PORT" to REDIS_PORT.toString(),
        "REDIS_PASSWORD" to redisPassord
    )

    val redisService
        get() = RedisService(
            TestContainerHelper.redis.container.host,
            TestContainerHelper.redis.container.firstMappedPort,
            TestContainerHelper.redis.redisPassord
        )

    val container = GenericContainer(
        DockerImageName.parse("redis:6.2.12-alpine")
    )
        .withNetwork(network)
        .withNetworkAliases(networkAlias)
        .withLogConsumer(Slf4jLogConsumer(TestContainerHelper.log).withPrefix(networkAlias).withSeparateOutputStreams())
        .withExposedPorts(REDIS_PORT)
        .apply {
            start()
        }
}
