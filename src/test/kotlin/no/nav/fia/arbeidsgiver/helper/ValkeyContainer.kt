package no.nav.fia.arbeidsgiver.helper

import no.nav.fia.arbeidsgiver.konfigurasjon.jedisPool
import no.nav.fia.arbeidsgiver.samarbeidsstatus.domene.SamarbeidsstatusService
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.SpørreundersøkelseService
import no.nav.fia.arbeidsgiver.valkey.ValkeyService
import org.slf4j.Logger
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.utility.DockerImageName

private const val VALKEY_PORT = 6379

class ValkeyContainer(
    network: Network,
    log: Logger,
) {
    private val networkAlias = "valkeyContainer"
    val container: GenericContainer<*> = GenericContainer(DockerImageName.parse("valkey/valkey"))
        .withNetwork(network)
        .withExposedPorts(VALKEY_PORT)
        .withNetworkAliases(networkAlias)
        .withLogConsumer(
            Slf4jLogConsumer(log)
                .withPrefix(networkAlias)
                .withSeparateOutputStreams(),
        )
        .withEnv(
            mapOf(
                "ALLOW_EMPTY_PASSWORD" to "yes",
                "VALKEY_DISABLE_COMMANDS" to "FLUSHDB,FLUSHALL",
            ),
        )
        .apply {
            start()
        }

    fun envVars() =
        mapOf(
            "VALKEY_HOST_FIA_SAMARBEIDSSTATUS" to networkAlias,
            "VALKEY_PORT_FIA_SAMARBEIDSSTATUS" to "$VALKEY_PORT",
            "VALKEY_USERNAME_FIA_SAMARBEIDSSTATUS" to "default",
            "VALKEY_PASSWORD_FIA_SAMARBEIDSSTATUS" to "",
        )

    private val valkeyService by lazy {
        ValkeyService(
            jedisPool(
                host = container.host,
                port = container.firstMappedPort,
                username = "default",
                password = "",
                ssl = false,
            ),
        )
    }

    val spørreundersøkelseService by lazy {
        SpørreundersøkelseService(valkeyService)
    }

    val samarbeidsstatusService by lazy {
        SamarbeidsstatusService(valkeyService)
    }
}
