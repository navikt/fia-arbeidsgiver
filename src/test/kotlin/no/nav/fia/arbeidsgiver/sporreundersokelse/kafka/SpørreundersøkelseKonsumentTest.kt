package no.nav.fia.arbeidsgiver.sporreundersokelse.kafka

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.equals.shouldNotBeEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.runBlocking
import no.nav.fia.arbeidsgiver.helper.TestContainerHelper.Companion.applikasjon
import no.nav.fia.arbeidsgiver.helper.TestContainerHelper.Companion.kafka
import no.nav.fia.arbeidsgiver.helper.TestContainerHelper.Companion.shouldContainLog
import no.nav.fia.arbeidsgiver.helper.TestContainerHelper.Companion.valkey
import no.nav.fia.arbeidsgiver.http.Feil
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.Spørsmål
import java.util.UUID
import kotlin.test.Test

class SpørreundersøkelseKonsumentTest {
    @Test
    fun `skal kunne konsumere evaluering med plan`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val sendtEvaluering = kafka.sendEvaluering(spørreundersøkelseId = spørreundersøkelseId)

        val evaluering = valkey.spørreundersøkelseService.hentePågåendeSpørreundersøkelse(spørreundersøkelseId)
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
            applikasjon.shouldContainLog(
                "Mottok spørreundersøkelse med type: 'Evaluering'".toRegex(),
            )

            val evaluering = valkey.spørreundersøkelseService.hentePågåendeSpørreundersøkelse(spørreundersøkelseId)
            evaluering.id shouldBe spørreundersøkelseId
            evaluering.type shouldBe "Evaluering"
            evaluering.temaer.forEach {
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
            applikasjon.shouldContainLog("Mottok spørreundersøkelse med type: 'Behovsvurdering'".toRegex())
            val behovsvurdering = valkey.spørreundersøkelseService.hentePågåendeSpørreundersøkelse(spørreundersøkelseId)
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
            val result = valkey.spørreundersøkelseService.hentePågåendeSpørreundersøkelse(id)
            result.id shouldBe id
        }
    }

    @Test
    fun `skal håndtere slettede kartlegginger`() {
        val id = UUID.randomUUID()
        kafka.sendSpørreundersøkelse(spørreundersøkelseId = id)

        val spørreundersøkelse =
            valkey.spørreundersøkelseService.henteSpørreundersøkelse(id)
        spørreundersøkelse.id shouldBe id.toString()

        kafka.sendSlettemeldingForSpørreundersøkelse(spørreundersøkelseId = id)
        shouldThrow<Feil> {
            valkey.spørreundersøkelseService.henteSpørreundersøkelse(id)
        }
        valkey.spørreundersøkelseService.hentAntallDeltakere(id) shouldBe 0
    }
}
