package no.nav.fia.arbeidsgiver.sporreundersokelse.kafka

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import no.nav.fia.arbeidsgiver.konfigurasjon.ApplikasjonsHelse
import no.nav.fia.arbeidsgiver.konfigurasjon.Kafka
import no.nav.fia.arbeidsgiver.konfigurasjon.Topic
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.evaluering.PlanDto
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.Spørreundersøkelse
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.SpørreundersøkelseService
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.Spørsmål
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.Svaralternativ
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.Tema
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.errors.RetriableException
import org.apache.kafka.common.errors.WakeupException
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.UUID
import kotlin.coroutines.CoroutineContext

class SpørreundersøkelseKonsument(
    val spørreundersøkelseService: SpørreundersøkelseService,
    val applikasjonsHelse: ApplikasjonsHelse,
    val kafka: Kafka,
) : CoroutineScope {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)
    private val job: Job = Job()
    private val topic = Topic.SPØRREUNDERSØKELSE
    private val kafkaConsumer = KafkaConsumer(
        kafka.consumerProperties(konsumentGruppe = topic.konsumentGruppe),
        StringDeserializer(),
        StringDeserializer(),
    )
    private val json = Json {
        ignoreUnknownKeys = true
    }

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO + job

    init {
        Runtime.getRuntime().addShutdownHook(Thread(this::cancel))
    }

    fun run() {
        launch {
            kafkaConsumer.use { consumer ->
                consumer.subscribe(listOf(topic.navn))
                logger.info("Kafka consumer subscribed to ${topic.navn}")

                while (applikasjonsHelse.alive) {
                    try {
                        val records = consumer.poll(Duration.ofSeconds(1))
                        if (records.count() < 1) continue
                        logger.debug("Fant ${records.count()} nye meldinger i topic: ${topic.navn}")

                        records.forEach { record ->
                            try {
                                val spørreundersøkelse =
                                    json.decodeFromString<SerializableSpørreundersøkelse>(
                                        record.value(),
                                    )
                                logger.debug("Mottok spørreundersøkelse med type: '${spørreundersøkelse.type}'")
                                when (spørreundersøkelse.type) {
                                    "Evaluering", "Behovsvurdering" -> {
                                        if (spørreundersøkelse.status == Spørreundersøkelse.Status.SLETTET) {
                                            logger.info("Sletter spørreundersøkelse med id: ${spørreundersøkelse.id}")
                                            spørreundersøkelseService.slett(spørreundersøkelse)
                                        } else {
                                            logger.debug("Lagrer spørreundersøkelse med id: ${spørreundersøkelse.id}")
                                            spørreundersøkelseService.lagre(spørreundersøkelse)
                                        }
                                    }
                                    else -> {
                                        logger.warn("Ukjent type spørreundersøkelse: ${spørreundersøkelse.type}")
                                    }
                                }
                            } catch (e: IllegalArgumentException) {
                                logger.error("Mottok feil formatert kafkamelding i topic: ${topic.navn}", e)
                            }
                        }
                        logger.debug("Prosesserte ${records.count()} meldinger i topic: ${topic.navn}")
                    } catch (e: WakeupException) {
                        logger.info("SpørreundersøkelseKonsument is shutting down", e)
                    } catch (e: RetriableException) {
                        logger.warn("Had a retriable exception, retrying", e)
                    } catch (e: Exception) {
                        logger.error("Exception is shutting down kafka listner for ${topic.navn}", e)
                        applikasjonsHelse.ready = false
                        applikasjonsHelse.alive = false
                    }
                }
            }
        }
    }

    @Serializable
    data class SerializableSpørreundersøkelse(
        val id: String,
        val orgnummer: String,
        val samarbeidsNavn: String,
        val virksomhetsNavn: String,
        val status: Spørreundersøkelse.Status,
        val temaer: List<SerializableTema>,
        val type: String,
        val plan: PlanDto?,
    ) {
        fun tilDomene() =
            Spørreundersøkelse(
                id = UUID.fromString(id),
                orgnummer = orgnummer,
                virksomhetsNavn = virksomhetsNavn,
                samarbeidsNavn = samarbeidsNavn,
                status = status,
                type = type,
                plan = plan,
                temaer = temaer.map { it.tilDomene() },
            )
    }

    @Serializable
    data class SerializableTema(
        val id: Int,
        val navn: String,
        val spørsmål: List<SerializableSpørsmål>,
    ) {
        fun tilDomene() =
            Tema(
                id = id,
                navn = navn,
                spørsmål = spørsmål.map { it.tilDomene() },
            )
    }

    @Serializable
    data class SerializableSpørsmål(
        val id: String,
        val tekst: String,
        val flervalg: Boolean,
        val svaralternativer: List<SerializableSvaralternativ>,
        val kategori: String? = null,
    ) {
        fun tilDomene() =
            Spørsmål(
                id = UUID.fromString(id),
                tekst = tekst,
                svaralternativer = svaralternativer.map { it.tilDomene() },
                flervalg = flervalg,
                kategori = kategori ?: "",
            )
    }

    @Serializable
    data class SerializableSvaralternativ(
        val id: String,
        val tekst: String,
    ) {
        fun tilDomene() =
            Svaralternativ(
                id = UUID.fromString(id),
                svartekst = tekst,
            )
    }

    private fun cancel() =
        runBlocking {
            logger.info("Stopping kafka consumer job for ${topic.navn}")
            kafkaConsumer.wakeup()
            job.cancelAndJoin()
            logger.info("Stopped kafka consumer job for ${topic.navn}")
        }
}
