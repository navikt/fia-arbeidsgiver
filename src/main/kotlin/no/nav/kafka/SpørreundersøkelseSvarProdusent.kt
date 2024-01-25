package no.nav.kafka

import no.nav.konfigurasjon.KafkaConfig


class SpørreundersøkelseSvarProdusent {

    private val kafkaProdusent = KafkaProdusent(kafkaConfig = KafkaConfig())

    fun sendSvar(svar: SpørreundersøkelseSvar) {
        val topic = Topic.SPØRREUNDERSØKELSE_SVAR
        kafkaProdusent.sendMelding(topic, svar.tilNøkkel(), svar.tilMelding())
    }
}
