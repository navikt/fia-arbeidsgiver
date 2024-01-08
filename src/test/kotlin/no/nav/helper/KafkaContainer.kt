package no.nav.helper

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.toKotlinLocalDateTime
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import no.nav.kafka.IASakStatus
import no.nav.konfigurasjon.Kafka
import no.nav.konfigurasjon.Kafka.Companion.sakStatusTopic
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.serialization.StringSerializer
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy
import org.testcontainers.utility.DockerImageName
import java.time.LocalDateTime
import java.util.*

class KafkaContainer(network: Network) {
    private val kafkaNetworkAlias = "kafkaContainer"
    private var adminClient: AdminClient
    private var kafkaProducer: KafkaProducer<String, String>

    val container = KafkaContainer(
        DockerImageName.parse("confluentinc/cp-kafka:7.4.3")
    )
        .withNetwork(network)
        .withNetworkAliases(kafkaNetworkAlias)
        .withLogConsumer(Slf4jLogConsumer(TestContainerHelper.log).withPrefix(kafkaNetworkAlias).withSeparateOutputStreams())
        .withEnv(
            mapOf(
                "KAFKA_AUTO_LEADER_REBALANCE_ENABLE" to "false",
                "KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS" to "1",
                "TZ" to TimeZone.getDefault().id
            )
        )
        .withCreateContainerCmdModifier { cmd -> cmd.withName("$kafkaNetworkAlias-${System.currentTimeMillis()}") }
        .waitingFor(HostPortWaitStrategy())
        .apply {
            start()
            adminClient = AdminClient.create(mapOf(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to this.bootstrapServers))
            createTopic()
            kafkaProducer = producer()
        }

    fun getEnv() = mapOf(
        "KAFKA_BROKERS" to "BROKER://$kafkaNetworkAlias:9092,PLAINTEXT://$kafkaNetworkAlias:9092",
        "KAFKA_TRUSTSTORE_PATH" to "",
        "KAFKA_KEYSTORE_PATH" to "",
        "KAFKA_CREDSTORE_PASSWORD" to "",
    )

    fun sendStatusOppdateringForVirksomhet(
        orgnr: String,
        status: String,
        sistOppdatert: LocalDateTime = LocalDateTime.now()
    ) {
        val iaStatusOppdatering = IASakStatus(
            orgnr = orgnr,
            saksnummer = "sak",
            status = status,
            sistOppdatert = sistOppdatert.toKotlinLocalDateTime()
        )
        TestContainerHelper.kafka.sendOgVent(
            nøkkel = orgnr,
            melding = Json.encodeToString(iaStatusOppdatering)
        )
    }

    fun sendOgVent(
        nøkkel: String,
        melding: String,
        topic: String = sakStatusTopic,
    ) {
        runBlocking {
            kafkaProducer.send(ProducerRecord("${Kafka.topicPrefix}.$topic", nøkkel, melding)).get()
            delay(timeMillis = 20L)
        }
    }

    private fun createTopic() {
        adminClient.createTopics(listOf(
            NewTopic(sakStatusTopic, 1, 1.toShort())
        ))
    }

    private fun KafkaContainer.producer(): KafkaProducer<String, String> =
        KafkaProducer(
            mapOf(
                CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG to this.bootstrapServers,
                CommonClientConfigs.SECURITY_PROTOCOL_CONFIG to "PLAINTEXT",
                ProducerConfig.ACKS_CONFIG to "all",
                ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION to "1",
                ProducerConfig.LINGER_MS_CONFIG to "0",
                ProducerConfig.RETRIES_CONFIG to "0",
                ProducerConfig.BATCH_SIZE_CONFIG to "1",
                SaslConfigs.SASL_MECHANISM to "PLAIN"
            ),
            StringSerializer(),
            StringSerializer()
        )
}
