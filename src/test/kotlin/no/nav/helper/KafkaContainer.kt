package no.nav.helper

import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.time.withTimeout
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.datetime.toKotlinLocalDateTime
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import no.nav.domene.sporreundersokelse.Spørreundersøkelse
import no.nav.domene.sporreundersokelse.SpørsmålOgSvaralternativer
import no.nav.domene.sporreundersokelse.Svaralternativ
import no.nav.domene.samarbeidsstatus.IASakStatus
import no.nav.domene.sporreundersokelse.SpørreundersøkelseStatus
import no.nav.kafka.Topic
import no.nav.konfigurasjon.KafkaConfig
import no.nav.persistence.KategoristatusDTO
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy
import org.testcontainers.utility.DockerImageName
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

class KafkaContainer(network: Network) {
    private val kafkaNetworkAlias = "kafkaContainer"
    private var adminClient: AdminClient
    private var kafkaProducer: KafkaProducer<String, String>

    val container: KafkaContainer = KafkaContainer(
        DockerImageName.parse("confluentinc/cp-kafka:7.4.3")
    )
        .withKraft()
        .withNetwork(network)
        .withNetworkAliases(kafkaNetworkAlias)
        .withLogConsumer(Slf4jLogConsumer(TestContainerHelper.log).withPrefix(kafkaNetworkAlias).withSeparateOutputStreams())
        .withEnv(
            mutableMapOf(
                "KAFKA_LOG4J_LOGGERS" to "org.apache.kafka.image.loader.MetadataLoader=WARN",
                "KAFKA_AUTO_LEADER_REBALANCE_ENABLE" to "false",
                "KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS" to "1",
                "TZ" to TimeZone.getDefault().id,
            )
        )
        .withCreateContainerCmdModifier { cmd -> cmd.withName("$kafkaNetworkAlias-${System.currentTimeMillis()}") }
        .waitingFor(HostPortWaitStrategy())
        .apply {
            start()
            adminClient = AdminClient.create(mapOf(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to this.bootstrapServers))
            createTopics()
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
            melding = Json.encodeToString(iaStatusOppdatering),
            topic = Topic.SAK_STATUS
        )
    }

    fun sendSpørreundersøkelse(
        spørreundersøkelseId: UUID,
        spørreundersøkelsesStreng: String = enStandardSpørreundersøkelse(spørreundersøkelseId),
    ) {
        sendOgVent(
            nøkkel = spørreundersøkelseId.toString(),
            melding = spørreundersøkelsesStreng,
            topic = Topic.SPØRREUNDERSØKELSE
        )
    }

    fun enStandardSpørreundersøkelse(
        spørreundersøkelseId: UUID,
        vertId: UUID = UUID.randomUUID(),
        spørreundersøkelseStatus: SpørreundersøkelseStatus = SpørreundersøkelseStatus.OPPRETTET,
    ): String {
        val spørreundersøkelse = Spørreundersøkelse(
            spørreundersøkelseId = spørreundersøkelseId,
            vertId = vertId,
            type = "kartlegging",
            spørsmålOgSvaralternativer = listOf(
                SpørsmålOgSvaralternativer(
                    id = UUID.randomUUID(),
                    kategori = KategoristatusDTO.Kategori.PARTSSAMARBEID.name,
                    spørsmål = "Hva gjør dere med IA?",
                    antallSvar = 2,
                    svaralternativer = listOf(
                        Svaralternativ(
                            svarId = UUID.randomUUID(),
                            "ingenting"
                        ),
                        Svaralternativ(
                            svarId = UUID.randomUUID(),
                            "alt"
                        ),
                    )
                )
            ),
            status = spørreundersøkelseStatus,
            avslutningsdato = LocalDate.now().toKotlinLocalDate()
        )
        return Json.encodeToString(spørreundersøkelse)
    }

    private fun sendOgVent(
        nøkkel: String,
        melding: String,
        topic: Topic,
    ) {
        runBlocking {
            kafkaProducer.send(ProducerRecord(topic.navnMedNamespace, nøkkel, melding)).get()
            delay(timeMillis = 30L)
        }
    }

    private fun createTopics() {
        adminClient.createTopics(listOf(
            NewTopic(Topic.SAK_STATUS.navn, 1, 1.toShort()),
            NewTopic(Topic.SPØRREUNDERSØKELSE.navn, 1, 1.toShort()),
            NewTopic(Topic.SPØRREUNDERSØKELSE_SVAR.navn, 1, 1.toShort())
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

    fun nyKonsument(topic: Topic) =
        KafkaConfig(
            brokers = container.bootstrapServers,
            truststoreLocation = "",
            keystoreLocation = "",
            credstorePassword = "",
        )
            .consumerProperties(konsumentGruppe = topic.konsumentGruppe)
            .let { config ->
                KafkaConsumer(config, StringDeserializer(), StringDeserializer())
            }

    suspend fun ventOgKonsumerKafkaMeldinger(
        key: String,
        konsument: KafkaConsumer<String, String>,
        block: (meldinger: List<String>) -> Unit,
    ) {
        withTimeout(Duration.ofSeconds(5)) {
            launch {
                while (this.isActive) {
                    val records = konsument.poll(Duration.ofMillis(50))
                    val meldinger = records
                        .filter { it.key() == key }
                        .map { it.value() }
                    if (meldinger.isNotEmpty()) {
                        block(meldinger)
                        break
                    }
                }
            }
        }
    }
}
