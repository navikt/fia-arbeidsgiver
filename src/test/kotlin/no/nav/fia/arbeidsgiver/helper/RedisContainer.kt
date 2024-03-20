package no.nav.fia.arbeidsgiver.helper

import no.nav.fia.arbeidsgiver.redis.RedisService
import no.nav.fia.arbeidsgiver.samarbeidsstatus.domene.SamarbeidsstatusService
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.SpørreundersøkelseService
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

    private val redisService by lazy {
        RedisService(
            url = "redis://${TestContainerHelper.redis.container.host}:${TestContainerHelper.redis.container.firstMappedPort}",
            username = redisUsername,
            password = redisPassord,
        )
    }

    val spørreundersøkelseService by lazy {
        SpørreundersøkelseService(redisService)
    }

    val samarbeidsstatusService by lazy {
        SamarbeidsstatusService(redisService)
    }

    val container = GenericContainer(
        DockerImageName.parse("redis:7.2.4-alpine")
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
