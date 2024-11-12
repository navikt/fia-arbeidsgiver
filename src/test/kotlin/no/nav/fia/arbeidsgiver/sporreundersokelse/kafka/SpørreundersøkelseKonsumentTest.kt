package no.nav.fia.arbeidsgiver.sporreundersokelse.kafka

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.equals.shouldNotBeEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.runBlocking
import no.nav.fia.arbeidsgiver.helper.TestContainerHelper.Companion.fiaArbeidsgiverApi
import no.nav.fia.arbeidsgiver.helper.TestContainerHelper.Companion.kafka
import no.nav.fia.arbeidsgiver.helper.TestContainerHelper.Companion.redis
import no.nav.fia.arbeidsgiver.helper.TestContainerHelper.Companion.shouldContainLog
import no.nav.fia.arbeidsgiver.http.Feil
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.Spørsmål
import java.util.UUID
import kotlin.test.Test

class SpørreundersøkelseKonsumentTest {
    @Test
    fun `skal kunne konsumere evaluering med plan`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val sendtEvaluering = kafka.sendEvaluering(spørreundersøkelseId = spørreundersøkelseId)

        val evaluering = redis.spørreundersøkelseService.hentePågåendeSpørreundersøkelse(spørreundersøkelseId)
        evaluering.id shouldBe spørreundersøkelseId
        evaluering.type shouldBe "Evaluering"
        evaluering.temaer.forEach {
            it.navn shouldNotBe null
            it.spørsmål shouldNotBe emptyList<Spørsmål>()
        }
        evaluering.plan shouldNotBe null
        evaluering.plan?.id shouldBe sendtEvaluering.plan?.id
    }

    @Test
    fun `skal kunne konsumere evaluering og logge`() {
        val spørreundersøkelseId = UUID.randomUUID()
        kafka.sendEvaluering(spørreundersøkelseId = spørreundersøkelseId)

        runBlocking {
            fiaArbeidsgiverApi.shouldContainLog(
                "Mottok spørreundersøkelse med type: Evaluering".toRegex(),
            )

            val evaluering = redis.spørreundersøkelseService.hentePågåendeSpørreundersøkelse(spørreundersøkelseId)
            evaluering.id shouldBe spørreundersøkelseId
            evaluering.type shouldBe "Evaluering"
            evaluering.temaer.forEach {
                it.navn shouldNotBe null
                it.spørsmål shouldNotBe emptyList<Spørsmål>()
            }
        }
    }

    @Test
    fun `skal kunne konsumere meldinger og lagre dem i Redis`() {
        // TODO: Denne testen skal(tm) kunne slettes onsdag 30.oktober 2024 da ingen meldinger produseres uten type lenger

        val spørreundersøkelseId = UUID.randomUUID()
        kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId)

        runBlocking {
            fiaArbeidsgiverApi.shouldContainLog(
                "Mottok spørreundersøkelse med type: null".toRegex(),
            )

            val behovsvurdering =
                redis.spørreundersøkelseService.hentePågåendeSpørreundersøkelse(spørreundersøkelseId)
            behovsvurdering.id shouldBe spørreundersøkelseId
            behovsvurdering.type shouldBe "Behovsvurdering"
            behovsvurdering.temaer.forEach {
                it.navn shouldNotBe null
                it.spørsmål shouldNotBe emptyList<Spørsmål>()
            }
        }
    }

    @Test
    fun `skal kunne konsumere nye meldinger med type og lagre dem i Redis`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val spørreundersøkelse = kafka.enStandardSpørreundersøkelse(spørreundersøkelseId, type = "Behovsvurdering")
        kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId, spørreundersøkelse = spørreundersøkelse)

        runBlocking {
            fiaArbeidsgiverApi.shouldContainLog(
                "Mottok spørreundersøkelse med type: Behovsvurdering".toRegex(),
            )
            val behovsvurdering =
                redis.spørreundersøkelseService.hentePågåendeSpørreundersøkelse(spørreundersøkelseId)
            behovsvurdering.id shouldBe spørreundersøkelseId
            behovsvurdering.type shouldBe "Behovsvurdering"
            behovsvurdering.temaer.forEach {
                it.navn shouldNotBe null
                it.spørsmål shouldNotBe emptyList<Spørsmål>()
            }
        }
    }

    @Test
    fun `skal kunne konsumere meldinger med ukjente felt`() {
        val id = UUID.randomUUID()
        val spørreundersøkelse = kafka.enStandardSpørreundersøkelse(id)

        kafka.sendSpørreundersøkelse(
            spørreundersøkelseId = id,
            medEkstraFelt = true,
        ) shouldNotBeEqual spørreundersøkelse

        runBlocking {
            val result = redis.spørreundersøkelseService.hentePågåendeSpørreundersøkelse(id)
            result.id shouldBe id
        }
    }

    @Test
    fun `skal håndtere slettede kartlegginger`() {
        val spørreundersøkelseId = UUID.randomUUID()
        kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId)

        val spørreundersøkelse =
            redis.spørreundersøkelseService.henteSpørreundersøkelse(spørreundersøkelseId)
        spørreundersøkelse.spørreundersøkelseId shouldBe spørreundersøkelseId.toString()

        kafka.sendSlettemeldingForSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId)
        shouldThrow<Feil> {
            redis.spørreundersøkelseService.henteSpørreundersøkelse(spørreundersøkelseId)
        }
        redis.spørreundersøkelseService.hentAntallDeltakere(spørreundersøkelseId) shouldBe 0
    }
}
