package no.nav.fia.arbeidsgiver.sporreundersokelse.kafka

import no.nav.fia.arbeidsgiver.konfigurasjon.KafkaConfig
import no.nav.fia.arbeidsgiver.konfigurasjon.KafkaTopics
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.SpørreundersøkelseHendelse
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord


class SpørreundersøkelseHendelseProdusent(kafkaConfig: KafkaConfig) {
    private val producer: KafkaProducer<String, String> = KafkaProducer(kafkaConfig.producerProperties())

    init {
        Runtime.getRuntime().addShutdownHook(Thread {
            producer.close()
        })
    }

    fun <T> sendHendelse(hendelse: SpørreundersøkelseHendelse<T>) {
        producer.send(ProducerRecord(KafkaTopics.SPØRREUNDERSØKELSE_HENDELSE.navnMedNamespace, hendelse.tilNøkkel(), hendelse.tilMelding()))
    }
}
