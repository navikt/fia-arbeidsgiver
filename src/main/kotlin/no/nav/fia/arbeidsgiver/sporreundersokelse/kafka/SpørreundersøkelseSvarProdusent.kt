package no.nav.fia.arbeidsgiver.sporreundersokelse.kafka

import no.nav.fia.arbeidsgiver.kafka.KafkaProdusent
import no.nav.fia.arbeidsgiver.kafka.Topic
import no.nav.fia.arbeidsgiver.konfigurasjon.KafkaConfig


class SpørreundersøkelseSvarProdusent {

    private val kafkaProdusent = KafkaProdusent(kafkaConfig = KafkaConfig())

    fun sendSvar(svar: SpørreundersøkelseSvarDTO) {
        val topic = Topic.SPØRREUNDERSØKELSE_SVAR
        kafkaProdusent.sendMelding(topic, svar.tilNøkkel(), svar.tilMelding())
    }
}
