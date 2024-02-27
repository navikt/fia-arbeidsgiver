package no.nav.fia.arbeidsgiver.sporreundersokelse.kafka

import no.nav.fia.arbeidsgiver.konfigurasjon.KafkaConfig
import no.nav.fia.arbeidsgiver.konfigurasjon.KafkaTopics
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord


class SpørreundersøkelseSvarProdusent(kafkaConfig: KafkaConfig) {
    private val producer: KafkaProducer<String, String> = KafkaProducer(kafkaConfig.producerProperties())

    init {
        Runtime.getRuntime().addShutdownHook(Thread {
            producer.close()
        })
    }

    fun sendSvar(svar: SpørreundersøkelseSvarDTO) {
        val topic = KafkaTopics.SPØRREUNDERSØKELSE_SVAR
        producer.send(ProducerRecord(topic.navnMedNamespace, svar.tilNøkkel(), svar.tilMelding()))
    }
}
