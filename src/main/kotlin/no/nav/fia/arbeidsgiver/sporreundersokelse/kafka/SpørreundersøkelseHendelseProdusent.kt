package no.nav.fia.arbeidsgiver.sporreundersokelse.kafka

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import no.nav.fia.arbeidsgiver.konfigurasjon.KafkaConfig
import no.nav.fia.arbeidsgiver.konfigurasjon.KafkaTopics
import no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.SpørreundersøkelseSvarProdusent.SpørreundersøkelseSvarDTO
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

class SpørreundersøkelseHendelseProdusent(
    kafkaConfig: KafkaConfig,
) {
    private val topic: KafkaTopics = KafkaTopics.SPØRREUNDERSØKELSE_HENDELSE
    private val producer: KafkaProducer<String, String> = KafkaProducer(kafkaConfig.producerProperties(clientId = topic.konsumentGruppe))

    init {
        Runtime.getRuntime().addShutdownHook(
            Thread {
                producer.close()
            },
        )
    }

    fun <T> sendHendelse(hendelse: SpørreundersøkelseHendelse<T>) {
        producer.send(
            ProducerRecord(
                topic.navnMedNamespace,
                hendelse.tilNøkkel(),
                hendelse.tilMelding(),
            ),
        )
    }

    class StengTema(
        spørreundersøkelseId: String,
        temaId: Int,
    ) : SpørreundersøkelseHendelse<Int>(
            spørreundersøkelseId = spørreundersøkelseId,
            hendelsesType = HendelsType.STENG_TEMA,
            data = temaId,
        )

    class SvarPåSpørsmål(
        spørreundersøkelseId: String,
        svarPåSpørsmål: SpørreundersøkelseSvarDTO,
    ) : SpørreundersøkelseHendelse<SpørreundersøkelseSvarDTO>(
            spørreundersøkelseId = spørreundersøkelseId,
            hendelsesType = HendelsType.SVAR_PÅ_SPØRSMÅL,
            data = svarPåSpørsmål,
        )

    @Serializable
    sealed class SpørreundersøkelseHendelse<T>(
        val spørreundersøkelseId: String,
        val hendelsesType: HendelsType,
        val data: T,
    ) {
        fun tilNøkkel() =
            Json.encodeToString(
                SpørreundersøkelseHendelseNøkkel(
                    spørreundersøkelseId,
                    hendelsesType,
                ),
            )

        fun tilMelding() =
            when (this) {
                is StengTema -> Json.encodeToString<Int>(data)
                is SvarPåSpørsmål -> Json.encodeToString<SpørreundersøkelseSvarDTO>(data)
            }
    }

    enum class HendelsType {
        STENG_TEMA,
        SVAR_PÅ_SPØRSMÅL,
    }

    @Serializable
    data class SpørreundersøkelseHendelseNøkkel(
        val spørreundersøkelseId: String,
        val hendelsesType: HendelsType,
    )
}
