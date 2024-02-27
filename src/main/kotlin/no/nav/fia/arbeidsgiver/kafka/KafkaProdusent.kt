package no.nav.fia.arbeidsgiver.kafka

import no.nav.fia.arbeidsgiver.konfigurasjon.KafkaConfig
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

class KafkaProdusent(kafkaConfig: KafkaConfig) {
    private val producer: KafkaProducer<String, String> = KafkaProducer(kafkaConfig.producerProperties())

    init {
        Runtime.getRuntime().addShutdownHook(Thread {
            producer.close()
        })
    }

    fun sendMelding(topic: Topic, nøkkel: String, verdi: String) {
        producer.send(ProducerRecord(topic.navnMedNamespace, nøkkel, verdi))
    }
}
