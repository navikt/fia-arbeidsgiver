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
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.SpørreundersøkelseService
import no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.SpørreundersøkelseOppdateringKonsument.OppdateringsType.ANTALL_SVAR
import no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.SpørreundersøkelseOppdateringKonsument.OppdateringsType.RESULTATER_FOR_TEMA
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.errors.RetriableException
import org.apache.kafka.common.errors.WakeupException
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import kotlin.coroutines.CoroutineContext

class SpørreundersøkelseOppdateringKonsument(
    val spørreundersøkelseService: SpørreundersøkelseService,
    val applikasjonsHelse: ApplikasjonsHelse,
    val kafka: Kafka,
) : CoroutineScope {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)
    private val job: Job = Job()
    private val topic = Topic.SPØRREUNDERSØKELSE_OPPDATERING
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
                                val nøkkel = json.decodeFromString<SpørreundersøkelseOppdateringNøkkel>(record.key())
                                when (nøkkel.oppdateringsType) {
                                    RESULTATER_FOR_TEMA -> {
                                        val temaResultat = json.decodeFromString<TemaResultatDto>(record.value())
                                        logger.debug(
                                            "Lagrer resultat for spørreundersøkelse: ${nøkkel.spørreundersøkelseId} for tema ${temaResultat.id}",
                                        )
                                        spørreundersøkelseService.lagre(nøkkel.spørreundersøkelseId, temaResultat)
                                    }

                                    ANTALL_SVAR -> {
                                        val antallSvar = json.decodeFromString<SpørreundersøkelseAntallSvarDto>(record.value())
                                        logger.debug(
                                            "Lagrer antall svar for spørsmål: ${antallSvar.spørsmålId} i spørreundersøkelse: ${antallSvar.spørreundersøkelseId}",
                                        )
                                        spørreundersøkelseService.lagre(antallSvar)
                                    }
                                }
                            } catch (e: IllegalArgumentException) {
                                logger.error("Mottok feil formatert kafkamelding i topic: ${topic.navn}", e)
                            }
                        }
                        logger.debug("Prosesserte ${records.count()} meldinger i topic: ${topic.navn}")
                    } catch (e: WakeupException) {
                        logger.info("Konsument for ${topic.navn} is shutting down", e)
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
    data class SpørreundersøkelseAntallSvarDto(
        val spørreundersøkelseId: String,
        val spørsmålId: String,
        val antallSvar: Int,
    )

    @Serializable
    data class SpørreundersøkelseOppdateringNøkkel(
        val spørreundersøkelseId: String,
        val oppdateringsType: OppdateringsType,
    )

    enum class OppdateringsType {
        RESULTATER_FOR_TEMA,
        ANTALL_SVAR,
    }

    @Serializable
    data class TemaResultatDto(
        val id: Int,
        val navn: String,
        val spørsmålMedSvar: List<SpørsmålResultatDto>,
    )

    @Serializable
    data class SpørsmålResultatDto(
        val id: String,
        val tekst: String,
        val flervalg: Boolean,
        val svarListe: List<SvarResultatDto>,
        val kategori: String? = null,
    )

    @Serializable
    data class SvarResultatDto(
        val id: String,
        val tekst: String,
        val antallSvar: Int,
    )

    private fun cancel() =
        runBlocking {
            logger.info("Stopping kafka consumer job for ${topic.navn}")
            kafkaConsumer.wakeup()
            job.cancelAndJoin()
            logger.info("Stopped kafka consumer job for ${topic.navn}")
        }
}
