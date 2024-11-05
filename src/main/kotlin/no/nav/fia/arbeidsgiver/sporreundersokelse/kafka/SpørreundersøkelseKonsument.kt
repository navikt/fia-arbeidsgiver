package no.nav.fia.arbeidsgiver.sporreundersokelse.kafka

import ia.felles.integrasjoner.kafkameldinger.SpørreundersøkelseMelding
import ia.felles.integrasjoner.kafkameldinger.SpørreundersøkelseStatus
import ia.felles.integrasjoner.kafkameldinger.SpørsmålMelding
import ia.felles.integrasjoner.kafkameldinger.SvaralternativMelding
import ia.felles.integrasjoner.kafkameldinger.TemaMelding
import ia.felles.integrasjoner.kafkameldinger.Temanavn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import no.nav.fia.arbeidsgiver.konfigurasjon.ApplikasjonsHelse
import no.nav.fia.arbeidsgiver.konfigurasjon.KafkaConfig
import no.nav.fia.arbeidsgiver.konfigurasjon.KafkaTopics
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
) : CoroutineScope {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)
    private val job: Job = Job()
    private val topic = KafkaTopics.SPØRREUNDERSØKELSE
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
                                val spørreundersøkelse =
                                    json.decodeFromString<SerializableSpørreundersøkelse>(
                                        record.value(),
                                    )
                                logger.info("Mottok spørreundersøkelse med type: ${spørreundersøkelse.type}")
                                when (spørreundersøkelse.type) {
                                    "Evaluering", "Behovsvurdering", null -> {
                                        if (spørreundersøkelse.status == SpørreundersøkelseStatus.SLETTET) {
                                            logger.info("Sletter spørreundersøkelse med id: ${spørreundersøkelse.spørreundersøkelseId}")
                                            spørreundersøkelseService.slett(spørreundersøkelse)
                                        } else {
                                            logger.info("Lagrer spørreundersøkelse med id: ${spørreundersøkelse.spørreundersøkelseId}")
                                            spørreundersøkelseService.lagre(spørreundersøkelse)
                                        }
                                    }
                                    else -> {
                                        logger.warn("Ukjent type spørreundersøkelse: ${spørreundersøkelse.type}")
                                    }
                                }
                            } catch (e: IllegalArgumentException) {
                                logger.error("Mottok feil formatert kafkamelding i topic: ${topic.navn}, melding: '${record.value()}'", e)
                            }
                        }
                        logger.info("Prosesserte ${records.count()} meldinger i topic: ${topic.navn}")
                    } catch (e: WakeupException) {
                        logger.info("SpørreundersøkelseKonsument is shutting down")
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
        override val spørreundersøkelseId: String,
        override val orgnummer: String,
        override val virksomhetsNavn: String,
        override val status: SpørreundersøkelseStatus,
        override val temaMedSpørsmålOgSvaralternativer: List<SerializableTema>,
        override val type: String? = null,
        override val vertId: String? = null,
        override val avslutningsdato: LocalDate? = null,
        val samarbeidsNavn: String? = null,
        // TODO: oppdater ia-felles etter 14.11.2024 med nye felter når gamle har blitt konsumert
    ) : SpørreundersøkelseMelding {
        fun tilDomene() =
            Spørreundersøkelse(
                id = UUID.fromString(spørreundersøkelseId),
                orgnummer = orgnummer,
                virksomhetsNavn = virksomhetsNavn,
                samarbeidsNavn = samarbeidsNavn ?: virksomhetsNavn,
                status = status,
                type = type ?: "Behovsvurdering",
                temaer = temaMedSpørsmålOgSvaralternativer.map { it.tilDomene() },
            )
    }

    @Serializable
    data class SerializableTema(
        override val temaId: Int,
        override val temanavn: Temanavn? = null,
        override val beskrivelse: String? = null,
        override val navn: String? = beskrivelse,
        override val introtekst: String? = null,
        override val spørsmålOgSvaralternativer: List<SerializableSpørsmål>,
    ) : TemaMelding {
        fun tilDomene() =
            Tema(
                id = temaId,
                navn = navn ?: beskrivelse!!,
                spørsmål = spørsmålOgSvaralternativer.map { it.tilDomene() },
            )
    }

    @Serializable
    data class SerializableSpørsmål(
        override val id: String,
        override val spørsmål: String,
        override val flervalg: Boolean,
        override val svaralternativer: List<SerializableSvaralternativ>,
    ) : SpørsmålMelding {
        fun tilDomene() =
            Spørsmål(
                id = UUID.fromString(id),
                tekst = spørsmål,
                svaralternativer = svaralternativer.map { it.tilDomene() },
                flervalg = flervalg,
            )
    }

    @Serializable
    data class SerializableSvaralternativ(
        override val svarId: String,
        override val svartekst: String,
    ) : SvaralternativMelding {
        fun tilDomene() =
            Svaralternativ(
                id = UUID.fromString(svarId),
                svartekst = svartekst,
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
