package no.nav.fia.arbeidsgiver.samarbeidsstatus.kafka

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import no.nav.fia.arbeidsgiver.helper.TestContainerHelper
import java.time.LocalDateTime
import kotlin.test.Test

class FiaStatusKonsumentTest {
    @Test
    fun `skal kunne konsumere meldinger`() {
        val orgnr = "123456789"

        TestContainerHelper.kafka.sendStatusOppdateringForVirksomhet(
            orgnr = orgnr,
            status = "VURDERES",
        )

        runBlocking {
            val result = TestContainerHelper.valkey.samarbeidsstatusService.henteSakStatus(orgnr)
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
            sistOppdatert = sistOppdatert,
        )
        TestContainerHelper.kafka.sendStatusOppdateringForVirksomhet(
            orgnr = orgnr,
            status = "VURDERES",
            sistOppdatert = sistOppdatert.minusSeconds(1),
        )

        TestContainerHelper.valkey.samarbeidsstatusService.henteSakStatus(orgnr)?.status shouldBe "VI_BISTÅR"
    }
}
