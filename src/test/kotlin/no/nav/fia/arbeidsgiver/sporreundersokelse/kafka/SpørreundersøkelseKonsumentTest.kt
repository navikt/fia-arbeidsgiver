package no.nav.fia.arbeidsgiver.sporreundersokelse.kafka

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.equals.shouldNotBeEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.util.*
import kotlin.test.Test
import kotlinx.coroutines.runBlocking
import no.nav.fia.arbeidsgiver.helper.TestContainerHelper
import no.nav.fia.arbeidsgiver.http.Feil
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.Spørsmål

class SpørreundersøkelseKonsumentTest {
    @Test
    fun `skal kunne konsumere meldinger og lagre dem i Redis`() {
        val spørreundersøkelseId = UUID.randomUUID()
        TestContainerHelper.kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId)

        runBlocking {
            val result =
                TestContainerHelper.redis.spørreundersøkelseService.hentePågåendeSpørreundersøkelse(spørreundersøkelseId)
            result.id shouldBe spørreundersøkelseId
            result.temaer.forEach {
                it.navn shouldNotBe null
                it.spørsmål shouldNotBe emptyList<Spørsmål>()
            }
        }

        runBlocking {
            val result =
                TestContainerHelper.redis.spørreundersøkelseService.hentePågåendeSpørreundersøkelse(spørreundersøkelseId)
            result.id shouldBe spørreundersøkelseId
            result.temaer.forEach {
                it.navn shouldNotBe null
                it.spørsmål shouldNotBe emptyList<Spørsmål>()
            }
        }
    }

    @Test
    fun `skal kunne konsumere meldinger med ukjente felt`() {
        val id = UUID.randomUUID()
        val spørreundersøkelse = TestContainerHelper.kafka.enStandardSpørreundersøkelse(id)

        TestContainerHelper.kafka.sendSpørreundersøkelse(
            spørreundersøkelseId = id, medEkstraFelt = true
        ) shouldNotBeEqual spørreundersøkelse

        runBlocking {
            val result = TestContainerHelper.redis.spørreundersøkelseService.hentePågåendeSpørreundersøkelse(id)
            result.id shouldBe id
        }
    }

    @Test
    fun `skal håndtere slettede kartlegginger`() {
        val spørreundersøkelseId = UUID.randomUUID()
        TestContainerHelper.kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId)

        val spørreundersøkelse =
            TestContainerHelper.redis.spørreundersøkelseService.henteSpørreundersøkelse(spørreundersøkelseId)
        spørreundersøkelse.spørreundersøkelseId shouldBe spørreundersøkelseId.toString()

        TestContainerHelper.kafka.sendSlettemeldingForSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId)
        shouldThrow<Feil> {
            TestContainerHelper.redis.spørreundersøkelseService.henteSpørreundersøkelse(spørreundersøkelseId)
        }
        TestContainerHelper.redis.spørreundersøkelseService.hentAntallDeltakere(spørreundersøkelseId) shouldBe 0
    }
}