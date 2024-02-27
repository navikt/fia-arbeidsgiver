package no.nav.fia.arbeidsgiver.kafka

import no.nav.fia.arbeidsgiver.konfigurasjon.KafkaConfig


class SpørreundersøkelseSvarProdusent {

    private val kafkaProdusent = KafkaProdusent(kafkaConfig = KafkaConfig())

    fun sendSvar(svar: SpørreundersøkelseSvar) {
        val topic = Topic.SPØRREUNDERSØKELSE_SVAR
        kafkaProdusent.sendMelding(topic, svar.tilNøkkel(), svar.tilMelding())
    }
}
