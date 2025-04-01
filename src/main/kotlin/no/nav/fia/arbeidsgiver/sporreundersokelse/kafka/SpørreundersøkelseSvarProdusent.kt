package no.nav.fia.arbeidsgiver.sporreundersokelse.kafka

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import no.nav.fia.arbeidsgiver.konfigurasjon.Kafka
import no.nav.fia.arbeidsgiver.konfigurasjon.Topic
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

class SpørreundersøkelseSvarProdusent(
    kafka: Kafka,
) {
    private val topic: Topic = Topic.SPØRREUNDERSØKELSE_SVAR
    private val producer: KafkaProducer<String, String> = KafkaProducer(kafka.producerProperties(clientId = topic.konsumentGruppe))

    init {
        Runtime.getRuntime().addShutdownHook(
            Thread {
                producer.close()
            },
        )
    }

    fun sendSvar(svar: SpørreundersøkelseSvarDTO) {
        producer.send(ProducerRecord(topic.navn, svar.tilNøkkel(), svar.tilMelding()))
    }

    @Serializable
    data class SpørreundersøkelseSvarDTO(
        val spørreundersøkelseId: String,
        val sesjonId: String,
        val spørsmålId: String,
        val svarIder: List<String>,
    ) {
        fun tilNøkkel() = "${sesjonId}_$spørsmålId"

        fun tilMelding(): String = Json.encodeToString(this)
    }
}
