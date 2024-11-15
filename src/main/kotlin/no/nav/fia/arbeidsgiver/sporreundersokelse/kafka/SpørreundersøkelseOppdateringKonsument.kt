package no.nav.fia.arbeidsgiver.sporreundersokelse.kafka

import ia.felles.integrasjoner.kafkameldinger.oppdatering.SpørsmålResultatMelding
import ia.felles.integrasjoner.kafkameldinger.oppdatering.SvarResultatMelding
import ia.felles.integrasjoner.kafkameldinger.oppdatering.TemaResultatMelding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import no.nav.fia.arbeidsgiver.konfigurasjon.ApplikasjonsHelse
import no.nav.fia.arbeidsgiver.konfigurasjon.KafkaConfig
import no.nav.fia.arbeidsgiver.konfigurasjon.KafkaTopics
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
) : CoroutineScope {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)
    private val job: Job = Job()
    private val topic = KafkaTopics.SPØRREUNDERSØKELSE_OPPDATERING
    private val kafkaConsumer = KafkaConsumer(
        KafkaConfig().consumerProperties(konsumentGruppe = topic.konsumentGruppe),
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
                consumer.subscribe(listOf(topic.navnMedNamespace))
                logger.info("Kafka consumer subscribed to ${topic.navnMedNamespace}")

                while (applikasjonsHelse.alive) {
                    try {
                        val records = consumer.poll(Duration.ofSeconds(1))
                        if (records.count() < 1) continue
                        logger.info("Fant ${records.count()} nye meldinger i topic: ${topic.navn}")

                        records.forEach { record ->
                            try {
                                val nøkkel = json.decodeFromString<SpørreundersøkelseOppdateringNøkkel>(record.key())
                                when (nøkkel.oppdateringsType) {
                                    RESULTATER_FOR_TEMA -> {
                                        val temaResultat = json.decodeFromString<TemaResultatDto>(record.value())
                                        logger.info(
                                            "Lagrer resultat for spørreundersøkelse: ${nøkkel.spørreundersøkelseId} for tema ${temaResultat.id}",
                                        )
                                        spørreundersøkelseService.lagre(nøkkel.spørreundersøkelseId, temaResultat)
                                    }

                                    ANTALL_SVAR -> {
                                        val antallSvar =
                                            json.decodeFromString<SpørreundersøkelseAntallSvarDto>(record.value())
                                        logger.info(
                                            "Lagrer antall svar for spørsmål: ${antallSvar.spørsmålId} i spørreundersøkelse: ${antallSvar.spørreundersøkelseId}",
                                        )
                                        spørreundersøkelseService.lagre(antallSvar)
                                    }
                                }
                            } catch (e: IllegalArgumentException) {
                                logger.error("Mottok feil formatert kafkamelding i topic: ${topic.navn}", e)
                            }
                        }
                        logger.info("Prosesserte ${records.count()} meldinger i topic: ${topic.navn}")
                    } catch (e: WakeupException) {
                        logger.info("Konsument for ${topic.navn} is shutting down")
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
        override val id: Int,
        override val navn: String,
        override val spørsmålMedSvar: List<SpørsmålResultatDto>,
    ) : TemaResultatMelding

    @Serializable
    data class SpørsmålResultatDto(
        override val id: String,
        override val tekst: String,
        override val flervalg: Boolean,
        override val svarListe: List<SvarResultatDto>,
    ) : SpørsmålResultatMelding

    @Serializable
    data class SvarResultatDto(
        override val id: String,
        override val tekst: String,
        override val antallSvar: Int,
    ) : SvarResultatMelding

    private fun cancel() =
        runBlocking {
            logger.info("Stopping kafka consumer job for ${topic.navn}")
            kafkaConsumer.wakeup()
            job.cancelAndJoin()
            logger.info("Stopped kafka consumer job for ${topic.navn}")
        }
}
