package no.nav.fia.arbeidsgiver.helper

import ia.felles.integrasjoner.kafkameldinger.spørreundersøkelse.SpørreundersøkelseStatus
import ia.felles.integrasjoner.kafkameldinger.spørreundersøkelse.SpørreundersøkelseStatus.PÅBEGYNT
import ia.felles.integrasjoner.kafkameldinger.spørreundersøkelse.SpørreundersøkelseStatus.SLETTET
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.time.withTimeout
import kotlinx.datetime.toKotlinLocalDateTime
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import no.nav.fia.arbeidsgiver.konfigurasjon.KafkaConfig
import no.nav.fia.arbeidsgiver.konfigurasjon.KafkaTopics
import no.nav.fia.arbeidsgiver.samarbeidsstatus.domene.IASakStatus
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.evaluering.PlanDto
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.Spørsmål
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.Svaralternativ
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.Tema
import no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.SpørreundersøkelseKonsument.SerializableSpørreundersøkelse
import no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.SpørreundersøkelseKonsument.SerializableSpørsmål
import no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.SpørreundersøkelseKonsument.SerializableSvaralternativ
import no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.SpørreundersøkelseKonsument.SerializableTema
import no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.SpørreundersøkelseOppdateringKonsument.OppdateringsType.ANTALL_SVAR
import no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.SpørreundersøkelseOppdateringKonsument.OppdateringsType.RESULTATER_FOR_TEMA
import no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.SpørreundersøkelseOppdateringKonsument.SpørreundersøkelseAntallSvarDto
import no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.SpørreundersøkelseOppdateringKonsument.SpørreundersøkelseOppdateringNøkkel
import no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.SpørreundersøkelseOppdateringKonsument.SpørsmålResultatDto
import no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.SpørreundersøkelseOppdateringKonsument.SvarResultatDto
import no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.SpørreundersøkelseOppdateringKonsument.TemaResultatDto
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
import java.time.LocalDateTime
import java.util.TimeZone
import java.util.UUID

class KafkaContainer(
    network: Network,
) {
    private val kafkaNetworkAlias = "kafkaContainer"
    private var adminClient: AdminClient
    private var kafkaProducer: KafkaProducer<String, String>
    private val json = Json {
        ignoreUnknownKeys = true
    }

    val container: KafkaContainer = KafkaContainer(
        DockerImageName.parse("confluentinc/cp-kafka:7.6.0"),
    )
        .withKraft()
        .withNetwork(network)
        .withNetworkAliases(kafkaNetworkAlias)
        .withLogConsumer(
            Slf4jLogConsumer(TestContainerHelper.log).withPrefix(kafkaNetworkAlias).withSeparateOutputStreams(),
        )
        .withEnv(
            mutableMapOf(
                "KAFKA_LOG4J_LOGGERS" to "org.apache.kafka.image.loader.MetadataLoader=WARN",
                "KAFKA_AUTO_LEADER_REBALANCE_ENABLE" to "false",
                "KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS" to "1",
                "TZ" to TimeZone.getDefault().id,
            ),
        )
        .withCreateContainerCmdModifier { cmd -> cmd.withName("$kafkaNetworkAlias-${System.currentTimeMillis()}") }
        .waitingFor(HostPortWaitStrategy())
        .apply {
            start()
            adminClient = AdminClient.create(mapOf(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to this.bootstrapServers))
            createTopics()
            kafkaProducer = producer()
        }

    fun getEnv() =
        mapOf(
            "KAFKA_BROKERS" to "BROKER://$kafkaNetworkAlias:9092,PLAINTEXT://$kafkaNetworkAlias:9092",
            "KAFKA_TRUSTSTORE_PATH" to "",
            "KAFKA_KEYSTORE_PATH" to "",
            "KAFKA_CREDSTORE_PASSWORD" to "",
        )

    fun sendStatusOppdateringForVirksomhet(
        orgnr: String,
        status: String,
        sistOppdatert: LocalDateTime = LocalDateTime.now(),
    ) {
        val iaStatusOppdatering = IASakStatus(
            orgnr = orgnr,
            saksnummer = "sak",
            status = status,
            sistOppdatert = sistOppdatert.toKotlinLocalDateTime(),
        )
        TestContainerHelper.kafka.sendOgVent(
            nøkkel = orgnr,
            melding = json.encodeToString(iaStatusOppdatering),
            topic = KafkaTopics.SAK_STATUS,
        )
    }

    fun sendSpørreundersøkelse(
        spørreundersøkelseId: UUID,
        spørreundersøkelse: SerializableSpørreundersøkelse = enStandardSpørreundersøkelse(spørreundersøkelseId),
        medEkstraFelt: Boolean = false,
    ): SerializableSpørreundersøkelse {
        val spørreundersøkelsesStreng =
            if (!medEkstraFelt) {
                json.encodeToString<SerializableSpørreundersøkelse>(spørreundersøkelse)
            } else {
                json.encodeToString<SerializableSpørreundersøkelse>(spørreundersøkelse)
                    .replace("\"temanavn\"", "\"ukjentFelt\":\"X\",\"temanavn\"")
            }

        sendOgVent(
            nøkkel = spørreundersøkelseId.toString(),
            melding = spørreundersøkelsesStreng,
            topic = KafkaTopics.SPØRREUNDERSØKELSE,
        )
        return json.decodeFromString<SerializableSpørreundersøkelse>(spørreundersøkelsesStreng)
    }

    fun sendEvaluering(
        spørreundersøkelseId: UUID,
        spørreundersøkelse: SerializableSpørreundersøkelse = enStandardSpørreundersøkelse(
            spørreundersøkelseId,
            type = "Evaluering",
            plan = PlanDto(
                id = UUID.randomUUID().toString(),
                sistEndret = LocalDateTime.now().toKotlinLocalDateTime(),
                sistPublisert = null,
                temaer = listOf(),
            ),
        ),
        medEkstraFelt: Boolean = false,
    ): SerializableSpørreundersøkelse {
        val spørreundersøkelsesStreng =
            if (!medEkstraFelt) {
                json.encodeToString<SerializableSpørreundersøkelse>(spørreundersøkelse)
            } else {
                json.encodeToString<SerializableSpørreundersøkelse>(spørreundersøkelse)
                    .replace("\"temanavn\"", "\"ukjentFelt\":\"X\",\"temanavn\"")
            }

        sendOgVent(
            nøkkel = spørreundersøkelseId.toString(),
            melding = spørreundersøkelsesStreng,
            topic = KafkaTopics.SPØRREUNDERSØKELSE,
        )
        return json.decodeFromString<SerializableSpørreundersøkelse>(spørreundersøkelsesStreng)
    }

    fun sendAntallSvar(
        spørreundersøkelseId: String,
        spørsmålId: String,
        antallSvar: Int,
    ): SpørreundersøkelseAntallSvarDto {
        val antallSvarDto = SpørreundersøkelseAntallSvarDto(
            spørreundersøkelseId = spørreundersøkelseId,
            spørsmålId = spørsmålId,
            antallSvar = antallSvar,
        )
        sendOgVent(
            nøkkel = Json.encodeToString(
                SpørreundersøkelseOppdateringNøkkel(
                    spørreundersøkelseId,
                    ANTALL_SVAR,
                ),
            ),
            melding = json.encodeToString(antallSvarDto),
            topic = KafkaTopics.SPØRREUNDERSØKELSE_OPPDATERING,
        )
        return antallSvarDto
    }

    private fun Svaralternativ.tilKafkaResultatMelding(antallSvar: Int) =
        SvarResultatDto(
            id = id.toString(),
            tekst = svartekst,
            antallSvar = antallSvar,
        )

    private fun Spørsmål.tilKafkaResultatMelding(antallSvar: Int) =
        SpørsmålResultatDto(
            id = id.toString(),
            tekst = tekst,
            svarListe = svaralternativer.map { it.tilKafkaResultatMelding(antallSvar = antallSvar) },
            flervalg = flervalg,
        )

    private fun Tema.tilKafkaResultatMelding(antallSvar: Int) =
        TemaResultatDto(
            id = id,
            navn = navn,
            spørsmålMedSvar = spørsmål.map {
                it.tilKafkaResultatMelding(antallSvar = antallSvar)
            },
        )

    fun sendResultatPåTema(
        spørreundersøkelseId: UUID,
        antallSvarPerSpørsmål: Int,
        tema: Tema,
    ): TemaResultatDto {
        val nøkkel = Json.encodeToString(
            SpørreundersøkelseOppdateringNøkkel(
                spørreundersøkelseId = spørreundersøkelseId.toString(),
                oppdateringsType = RESULTATER_FOR_TEMA,
            ),
        )

        val temaResultatDto = tema.tilKafkaResultatMelding(antallSvar = antallSvarPerSpørsmål)

        sendOgVent(
            nøkkel = nøkkel,
            melding = json.encodeToString(temaResultatDto),
            topic = KafkaTopics.SPØRREUNDERSØKELSE_OPPDATERING,
        )
        return temaResultatDto
    }

    fun sendSlettemeldingForSpørreundersøkelse(spørreundersøkelseId: UUID) =
        sendSpørreundersøkelse(
            spørreundersøkelseId = spørreundersøkelseId,
            spørreundersøkelse = enStandardSpørreundersøkelse(
                id = spørreundersøkelseId,
                spørreundersøkelseStatus = SLETTET,
            ),
        )

    fun enStandardSpørreundersøkelse(
        id: UUID,
        orgnummer: String = AltinnProxyContainer.ALTINN_ORGNR_1,
        virksomhetsNavn: String = "Navn ${AltinnProxyContainer.ALTINN_ORGNR_1}",
        spørreundersøkelseStatus: SpørreundersøkelseStatus = PÅBEGYNT,
        temanavn: List<String> = listOf("Partssamarbeid", "Sykefravær", "Arbeidsmiljø"),
        flervalg: Boolean = false,
        type: String = "Behovsvurdering",
        plan: PlanDto? = null,
    ) = SerializableSpørreundersøkelse(
        id = id.toString(),
        orgnummer = orgnummer,
        samarbeidsNavn = "Navn på et samarbeid",
        virksomhetsNavn = virksomhetsNavn,
        status = spørreundersøkelseStatus,
        type = type,
        plan = plan,
        temaer = temanavn.mapIndexed { index, navn ->
            SerializableTema(
                id = index,
                navn = navn,
                spørsmål = listOf(
                    SerializableSpørsmål(
                        id = UUID.randomUUID().toString(),
                        tekst = "Hva gjør dere med IA?",
                        flervalg = flervalg,
                        svaralternativer = listOf(
                            SerializableSvaralternativ(
                                id = UUID.randomUUID().toString(),
                                tekst = "ingenting",
                            ),
                            SerializableSvaralternativ(
                                id = UUID.randomUUID().toString(),
                                tekst = "alt",
                            ),
                        ),
                    ),
                    SerializableSpørsmål(
                        id = UUID.randomUUID().toString(),
                        tekst = "Hva gjør dere IKKE med IA?",
                        flervalg = flervalg,
                        svaralternativer = listOf(
                            SerializableSvaralternativ(
                                id = UUID.randomUUID().toString(),
                                tekst = "noen ting",
                            ),
                            SerializableSvaralternativ(
                                id = UUID.randomUUID().toString(),
                                tekst = "alt",
                            ),
                        ),
                    ),
                ),
            )
        },
    )

    fun enStandardEvaluering(
        id: UUID,
        orgnummer: String = AltinnProxyContainer.ALTINN_ORGNR_1,
        virksomhetsNavn: String = "Navn ${AltinnProxyContainer.ALTINN_ORGNR_1}",
        spørreundersøkelseStatus: SpørreundersøkelseStatus = PÅBEGYNT,
        temanavn: List<String> = listOf("Partssamarbeid", "Arbeidsmiljø"),
        flervalg: Boolean = false,
        type: String = "Evaluering",
        plan: PlanDto? = null,
    ) = SerializableSpørreundersøkelse(
        id = id.toString(),
        orgnummer = orgnummer,
        samarbeidsNavn = "Navn på et samarbeid",
        virksomhetsNavn = virksomhetsNavn,
        status = spørreundersøkelseStatus,
        type = type,
        plan = plan,
        temaer = temanavn.mapIndexed { index, navn ->
            SerializableTema(
                id = index,
                navn = navn,
                spørsmål = listOf(
                    SerializableSpørsmål(
                        id = UUID.randomUUID().toString(),
                        tekst = "Hva gjør dere med IA?",
                        flervalg = flervalg,
                        svaralternativer = listOf(
                            SerializableSvaralternativ(
                                id = UUID.randomUUID().toString(),
                                tekst = "ingenting",
                            ),
                            SerializableSvaralternativ(
                                id = UUID.randomUUID().toString(),
                                tekst = "alt",
                            ),
                        ),
                        kategori = "Utvikle IA-arbeidet",
                    ),
                    SerializableSpørsmål(
                        id = UUID.randomUUID().toString(),
                        tekst = "Hva gjør dere IKKE med IA?",
                        flervalg = flervalg,
                        svaralternativer = listOf(
                            SerializableSvaralternativ(
                                id = UUID.randomUUID().toString(),
                                tekst = "noen ting",
                            ),
                            SerializableSvaralternativ(
                                id = UUID.randomUUID().toString(),
                                tekst = "alt",
                            ),
                        ),
                        kategori = "Veien videre",
                    ),
                ),
            )
        },
    )

    private fun sendOgVent(
        nøkkel: String,
        melding: String,
        topic: KafkaTopics,
    ) {
        runBlocking {
            kafkaProducer.send(ProducerRecord(topic.navnMedNamespace, nøkkel, melding)).get()
            delay(timeMillis = 30L)
        }
    }

    private fun createTopics() {
        adminClient.createTopics(
            listOf(
                NewTopic(KafkaTopics.SAK_STATUS.navn, 1, 1.toShort()),
                NewTopic(KafkaTopics.SPØRREUNDERSØKELSE.navn, 1, 1.toShort()),
                NewTopic(KafkaTopics.SPØRREUNDERSØKELSE_SVAR.navn, 1, 1.toShort()),
                NewTopic(KafkaTopics.SPØRREUNDERSØKELSE_HENDELSE.navn, 1, 1.toShort()),
            ),
        )
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
                SaslConfigs.SASL_MECHANISM to "PLAIN",
            ),
            StringSerializer(),
            StringSerializer(),
        )

    fun nyKonsument(topic: KafkaTopics) =
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
