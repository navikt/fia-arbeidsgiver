package no.nav.kafka

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
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
        val iaStatusOppdatering = IASakStatus(
            orgnr = orgnr,
            saksnummer = "sak",
            status = "VURDERES",
            sistOppdatert = LocalDateTime.now().toKotlinLocalDateTime()
        )
        val somString = Json.encodeToString(iaStatusOppdatering)

        TestContainerHelper.kafka.sendOgVentTilKonsumert(
            nøkkel = orgnr,
            melding = somString,
        )
        TestContainerHelper.fiaArbeidsgiverApi shouldContainLog "Fikk melding om virksomhet .*$orgnr.*".toRegex()
        runBlocking {
            val result = TestContainerHelper.redis.redisService.henteSakStatus(orgnr)
            result?.orgnr shouldBe orgnr
        }
    }

    @Test
    fun `skal ikke overskrive i redis dersom det allerede finnes en nyere versjon der`() {
        val orgnr = "555555555"
        val sistOppdatert = LocalDateTime.now()
        TestContainerHelper.kafka.sendStatusOppdateringForVirksomhet(
            orgnr = orgnr,
            status = "VI_BISTÅR",
            sistOppdatert = sistOppdatert
        )
        TestContainerHelper.kafka.sendStatusOppdateringForVirksomhet(
            orgnr = orgnr,
            status = "VURDERES",
            sistOppdatert = sistOppdatert.minusSeconds(1)
        )

        TestContainerHelper.redis.redisService.henteSakStatus(orgnr)?.status shouldBe "VI_BISTÅR"
    }

}
