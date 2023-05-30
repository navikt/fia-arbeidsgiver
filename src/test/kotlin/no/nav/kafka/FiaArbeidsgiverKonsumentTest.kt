package no.nav.kafka

import kotlinx.datetime.toKotlinLocalDateTime
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import no.nav.helper.TestContainerHelper
import no.nav.helper.TestContainerHelper.Companion.shouldContainLog
import java.time.LocalDateTime
import kotlin.test.Test


class FiaArbeidsgiverKonsumentTest {
    @Test
    fun `skal kunne konsumere meldinger`() {
        val orgnr = "123456789"
        val iaStatusOppdatering = IASakStatus(orgnr = orgnr, saksnummer = "sak", status = "VURDERES", sistOppdatert = LocalDateTime.now().toKotlinLocalDateTime())
        val somString = Json.encodeToString(iaStatusOppdatering)

        TestContainerHelper.kafka.sendOgVentTilKonsumert(orgnr, somString, "${Kafka.topicPrefix}.${Kafka.topic}", Kafka.consumerGroupId)
        TestContainerHelper.fiaArbeidsgiverApi shouldContainLog "Fikk melding om virksomhet .*$orgnr.*".toRegex()
    }

}