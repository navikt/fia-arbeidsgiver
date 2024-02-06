package no.nav.kafka

import io.kotest.matchers.equals.shouldNotBeEqual
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import no.nav.helper.TestContainerHelper
import java.util.*
import kotlin.test.Test


class SpørreundersøkelseKonsumentTest {
    @Test
    fun `skal kunne konsumere meldinger`() {
        val id = UUID.randomUUID()
        TestContainerHelper.kafka.sendSpørreundersøkelse(spørreundersøkelseId = id)

        runBlocking {
            val result = TestContainerHelper.redis.redisService.hentePågåendeSpørreundersøkelse(id)
            result.spørreundersøkelseId shouldBe id
        }
    }

    @Test
    fun `skal kunne konsumere meldinger med ukjente felt`() {
        val id = UUID.randomUUID()
        val spørreundersøkelse =  TestContainerHelper.kafka.enStandardSpørreundersøkelse(id)
        val spørreundersøkelseMedEkstraFelt =  spørreundersøkelse.replace("\"kategori\"", "\"ukjentFelt\":\"X\",\"kategori\"")

        spørreundersøkelseMedEkstraFelt shouldNotBeEqual  spørreundersøkelse

        TestContainerHelper.kafka.sendSpørreundersøkelse(spørreundersøkelseId = id, spørreundersøkelsesStreng = spørreundersøkelseMedEkstraFelt)

        runBlocking {
            val result = TestContainerHelper.redis.redisService.hentePågåendeSpørreundersøkelse(id)
            result.spørreundersøkelseId shouldBe id
        }
    }

    @Test
    fun `skal kunne konsumere meldinger når vertId mangler`() {
        val id = UUID.randomUUID()
        val vertId = UUID.randomUUID()
        val spørreundersøkelse =  TestContainerHelper.kafka.enStandardSpørreundersøkelse(
            spørreundersøkelseId = id,
            vertId = vertId,
        )
        val spørreundersøkelseUtenVertId = spørreundersøkelse.replace("\"vertId\":\"$vertId\",", "")

        spørreundersøkelseUtenVertId shouldNotBeEqual spørreundersøkelse

        TestContainerHelper.kafka.sendSpørreundersøkelse(spørreundersøkelseId = id, spørreundersøkelsesStreng = spørreundersøkelseUtenVertId)

        runBlocking {
            val result = TestContainerHelper.redis.redisService.hentePågåendeSpørreundersøkelse(id)
            result.spørreundersøkelseId shouldBe id
        }
    }

    @Test
    fun `skal kunne konsumere meldinger når antallSvar mangler`() {
        val id = UUID.randomUUID()
        val vertId = UUID.randomUUID()
        val spørreundersøkelse =  TestContainerHelper.kafka.enStandardSpørreundersøkelse(
            spørreundersøkelseId = id,
            vertId = vertId,
        )
        val spørreundersøkelseUtenVertId = spørreundersøkelse.replace("\"antallSvar\":2,", "")

        spørreundersøkelseUtenVertId shouldNotBeEqual spørreundersøkelse

        TestContainerHelper.kafka.sendSpørreundersøkelse(spørreundersøkelseId = id, spørreundersøkelsesStreng = spørreundersøkelseUtenVertId)

        runBlocking {
            val result = TestContainerHelper.redis.redisService.hentePågåendeSpørreundersøkelse(id)
            result.spørreundersøkelseId shouldBe id
        }
    }
}
