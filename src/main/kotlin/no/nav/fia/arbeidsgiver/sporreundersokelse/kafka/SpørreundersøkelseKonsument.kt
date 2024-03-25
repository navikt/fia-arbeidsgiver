package no.nav.fia.arbeidsgiver.sporreundersokelse.kafka

import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import no.nav.fia.arbeidsgiver.konfigurasjon.KafkaTopics
import no.nav.fia.arbeidsgiver.konfigurasjon.KafkaConfig
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.Spørreundersøkelse
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.SpørreundersøkelseService
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.SpørreundersøkelseStatus
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.SpørsmålOgSvaralternativer
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.Svaralternativ
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.Tema
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.TemaMedSpørsmålOgSvaralternativer
import no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.dto.SpørreundersøkelseDto
import no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.dto.SpørsmålOgSvaralternativerDto
import no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.dto.SvaralternativDto
import no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.dto.TemaMedSpørsmålOgSvaralternativerDto
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
                                val spørreundersøkelseDto = json.decodeFromString<SpørreundersøkelseDto>(record.value())

                                val spørreundersøkelse = spørreundersøkelseDto.tilDomene()

                                val spørreundersøkelseId = spørreundersøkelse.spørreundersøkelseId

                                if (spørreundersøkelse.status == SpørreundersøkelseStatus.SLETTET) {
                                    logger.info("Sletter spørreundersøkelse med id: $spørreundersøkelseId")
                                    spørreundersøkelseService.slett(spørreundersøkelse)
                                } else {
                                    logger.info("Lagrer spørreundersøkelse med id: $spørreundersøkelseId")
                                    spørreundersøkelseService.lagre(spørreundersøkelse)
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


    private fun cancel() = runBlocking {
        logger.info("Stopping kafka consumer job for ${topic.navn}")
        kafkaConsumer.wakeup()
        job.cancelAndJoin()
        logger.info("Stopped kafka consumer job for ${topic.navn}")
    }
}

private fun SpørreundersøkelseDto.tilDomene() = Spørreundersøkelse(
    spørreundersøkelseId = UUID.fromString(spørreundersøkelseId),
    vertId = UUID.fromString(vertId),
    temaMedSpørsmålOgSvaralternativer = temaMedSpørsmålOgSvaralternativer.map { it.tilDomene() },
    status = status,
    type = type,
    avslutningsdato = avslutningsdato,
)

private fun TemaMedSpørsmålOgSvaralternativerDto.tilDomene() = TemaMedSpørsmålOgSvaralternativer(
    tema = temanavn,
    beskrivelse = beskrivelse,
    introtekst = introtekst,
    spørsmålOgSvaralternativer = spørsmålOgSvaralternativer.map {
        it.tilDomene(temanavn)
    }
)

private fun SpørsmålOgSvaralternativerDto.tilDomene(tema: Tema) = SpørsmålOgSvaralternativer(
    id = UUID.fromString(id),
    tema = tema,
    spørsmål = spørsmål,
    svaralternativer = svaralternativer.map { it.tilDomene() },
)

private fun SvaralternativDto.tilDomene() = Svaralternativ(
    svarId = UUID.fromString(svarId),
    svartekst = svartekst,
)