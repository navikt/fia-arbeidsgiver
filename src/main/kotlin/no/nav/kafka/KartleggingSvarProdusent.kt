package no.nav.kafka

import no.nav.konfigurasjon.KafkaConfig


class KartleggingSvarProdusent {

    private val kafkaProdusent = KafkaProdusent(kafkaConfig = KafkaConfig())

    fun sendSvar(svar: KartleggingSvar) {
        val topic = Topic.KARTLEGGING_SVAR
        kafkaProdusent.sendMelding(topic, svar.tilNøkkel(), svar.tilMelding())
    }
}
