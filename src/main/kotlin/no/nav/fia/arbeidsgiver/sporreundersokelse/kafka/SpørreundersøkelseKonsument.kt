package no.nav.fia.arbeidsgiver.sporreundersokelse.kafka

import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import no.nav.fia.arbeidsgiver.konfigurasjon.KafkaTopics
import no.nav.fia.arbeidsgiver.konfigurasjon.KafkaConfig
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.KategoristatusDTO
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.Spørreundersøkelse
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.SpørreundersøkelseService
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.SpørreundersøkelseStatus
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.errors.RetriableException
import org.apache.kafka.common.errors.WakeupException
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.*
import kotlin.coroutines.CoroutineContext

class SpørreundersøkelseKonsument(val spørreundersøkelseService: SpørreundersøkelseService) : CoroutineScope {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)
    private val job: Job = Job()
    private val topic = KafkaTopics.SPØRREUNDERSØKELSE
    private val kafkaConsumer = KafkaConsumer(
        KafkaConfig().consumerProperties(konsumentGruppe = topic.konsumentGruppe),
        StringDeserializer(),
        StringDeserializer()
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

                while (job.isActive) {
                    try {
                        val records = consumer.poll(Duration.ofSeconds(1))
                        if (records.count() < 1) continue
                        logger.info("Fant ${records.count()} nye meldinger i topic: ${topic.navn}")

                        records.forEach { record ->
                            try {
                                val spørreundersøkelse = json.decodeFromString<Spørreundersøkelse>(record.value())
                                val spørreundersøkelseId = spørreundersøkelse.spørreundersøkelseId

                                if (spørreundersøkelse.status == SpørreundersøkelseStatus.SLETTET) {
                                    logger.info("Sletter spørreundersøkelse med id: $spørreundersøkelseId")
                                    spørreundersøkelseService.slett(spørreundersøkelse)
                                } else {
                                    logger.info("Lagrer spørreundersøkelse med id: $spørreundersøkelseId")
                                    spørreundersøkelseService.lagre(spørreundersøkelse)
                                    oppretteEllerLagreKategoristatus(spørreundersøkelse, spørreundersøkelseId)
                                }
                            } catch (e: IllegalArgumentException) {
                                logger.error("Mottok feil formatert kafkamelding i topic: ${topic.navn}", e)
                            }
                        }
                        logger.info("Prosesserte ${records.count()} meldinger i topic: ${topic.navn}")
                    } catch (e: WakeupException) {
                        logger.info("SpørreundersøkelseKonsument is shutting down")
                    } catch (e: RetriableException) {
                        logger.warn("Had a retriable exception, retrying", e)
                    } catch (e: Exception) {
                        logger.error("Exception is shutting down kafka listner for ${topic.navn}", e)
                        job.cancel(CancellationException(e.message))
                        job.join()
                        throw e
                    }
                }
            }
        }
    }

    private fun oppretteEllerLagreKategoristatus(
        payload: Spørreundersøkelse,
        spørreundersøkelseId: UUID
    ) {
        val kategorier = payload.spørsmålOgSvaralternativer.groupBy { it.kategori }
        kategorier.keys.forEach{ kategori ->
            val kategoristatus = spørreundersøkelseService.hentKategoristatus(
                spørreundersøkelseId = spørreundersøkelseId,
                kategori = kategori
            )

            if (kategoristatus == null) {
                val spørreundersøkelse = spørreundersøkelseService.henteSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId)
                val antallSpørsmålIKategori =
                    spørreundersøkelse.spørsmålOgSvaralternativer.filter { it.kategori == kategori }.size
                logger.info("Lagrer kategoristatus $kategoristatus for $kategori")
                spørreundersøkelseService.lagreKategoristatus(
                    spørreundersøkelseId = spørreundersøkelseId,
                    kategoristatus = KategoristatusDTO(
                        kategori = kategori,
                        status = KategoristatusDTO.Status.OPPRETTET,
                        spørsmålindeks = null,
                        antallSpørsmål = antallSpørsmålIKategori
                    )
                )
            }
        }
    }

    private fun cancel() = runBlocking {
        logger.info("Stopping kafka consumer job for ${topic.navn}")
        kafkaConsumer.wakeup()
        job.cancelAndJoin()
        logger.info("Stopped kafka consumer job for ${topic.navn}")
    }
}
