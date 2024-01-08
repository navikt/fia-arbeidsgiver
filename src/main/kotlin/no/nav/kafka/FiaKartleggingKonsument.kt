package no.nav.kafka

import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import no.nav.konfigurasjon.Kafka
import no.nav.persistence.RedisService
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.errors.RetriableException
import org.apache.kafka.common.errors.WakeupException
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import kotlin.coroutines.CoroutineContext

class FiaKartleggingKonsument(val redisService: RedisService) : CoroutineScope {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)
    private val job: Job = Job()
    private val kafkaConsumer = KafkaConsumer(
        Kafka.consumerProperties(),
        StringDeserializer(),
        StringDeserializer()
    )

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO + job

    init {
        Runtime.getRuntime().addShutdownHook(Thread(this::cancel))
    }

    fun run() {
        launch {
            kafkaConsumer.use { consumer ->
                consumer.subscribe(listOf("${Kafka.topicPrefix}.${Kafka.kartleggingTopic}"))
                logger.info("Kafka consumer subscribed to ${Kafka.topicPrefix}.${Kafka.kartleggingTopic}")

                while (job.isActive) {
                    try {
                        val records = consumer.poll(Duration.ofSeconds(1))
                        if (records.count() < 1) continue
                        logger.info("Fant ${records.count()} nye meldinger i topic: ${Kafka.kartleggingTopic}")

                        records.forEach {record ->
                            try {
                                val payload = Json.decodeFromString<Spørreundersøkelse>(record.value())
                                redisService.lagre(payload)
                            } catch (e: IllegalArgumentException) {
                                logger.error("Mottok feil formatert kafkamelding i topic: ${Kafka.kartleggingTopic}")
                            }
                        }
                        logger.info("Lagret ${records.count()} meldinger i topic: ${Kafka.kartleggingTopic}")
                    } catch (e: WakeupException) {
                        logger.info("FiaKartleggingKonsument is shutting down")
                    } catch (e: RetriableException) {
                        logger.warn("Had a retriable exception, retrying", e)
                    } catch (e: Exception) {
                        logger.error("Exception is shutting down kafka listner for ${Kafka.kartleggingTopic}", e)
                        job.cancel(CancellationException(e.message))
                        job.join()
                        throw e
                    }
                }
            }
        }
    }

    private fun cancel() = runBlocking {
        logger.info("Stopping kafka consumer job for ${Kafka.topicPrefix}.${Kafka.kartleggingTopic}")
        kafkaConsumer.wakeup()
        job.cancelAndJoin()
        logger.info("Stopped kafka consumer job for ${Kafka.topicPrefix}.${Kafka.kartleggingTopic}")
    }
}
