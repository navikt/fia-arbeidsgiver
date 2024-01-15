package no.nav.kafka

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import no.nav.helper.TestContainerHelper
import java.util.*
import kotlin.test.Test


class FiaKartleggingKonsumentTest {
    @Test
    fun `skal kunne konsumere meldinger`() {
        val id = UUID.randomUUID()
        TestContainerHelper.kafka.sendKartlegging(spørreundersøkelseId = id)

        runBlocking {
            val result = TestContainerHelper.redis.redisService.henteSpørreundersøkelse(id)
            result?.id shouldBe id
        }
    }
}
