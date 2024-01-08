package no.nav.kafka

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import no.nav.helper.TestContainerHelper
import no.nav.konfigurasjon.Kafka
import java.time.LocalDate.now
import java.util.*
import kotlin.test.Test


class FiaKartleggingKonsumentTest {
    @Test
    fun `skal kunne konsumere meldinger`() {
        val spørreundersøkelse = Spørreundersøkelse(
            id = UUID.randomUUID(),
            pinKode = "123456",
            spørsmålOgSvaralternativer = listOf(
                SpørsmålOgSvaralternativer(
                    id = UUID.randomUUID(),
                    spørsmål = "Hva gjør dere med IA?",
                    svaralternativer = listOf (
                        Svaralternativ(
                            id = UUID.randomUUID(),
                            "ingenting"
                        ),
                        Svaralternativ(
                            id = UUID.randomUUID(),
                            "alt"
                        ),
                    )
                )
            ),
            status = "aktiv",
            avslutningsdato = now().toKotlinLocalDate()
        )
        val somString = Json.encodeToString(spørreundersøkelse)

        TestContainerHelper.kafka.sendOgVent(
            nøkkel = spørreundersøkelse.id.toString(),
            melding = somString,
            topic = Kafka.kartleggingTopic
        )
        runBlocking {
            val result = TestContainerHelper.redis.redisService.henteSpørreundersøkelse(spørreundersøkelse.id)
            result?.id shouldBe spørreundersøkelse.id
        }
    }
}
