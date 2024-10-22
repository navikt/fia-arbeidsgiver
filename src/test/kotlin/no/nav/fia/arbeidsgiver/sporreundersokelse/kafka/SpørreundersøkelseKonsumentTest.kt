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
    fun `skal kunne konsumere evaluering og logge`() {
        // TODO: Sørg for at evaluering blir lagret i Redis
        val spørreundersøkelseId = UUID.randomUUID()
        val evaluering = kafka.sendEvaluering(spørreundersøkelseId = spørreundersøkelseId)

        runBlocking {
            fiaArbeidsgiverApi.shouldContainLog(
                "Mottok spørreundersøkelse med type: ${evaluering.type}".toRegex(),
            )
            fiaArbeidsgiverApi.shouldContainLog(
                "Evaluering er ikke implementert, ignorerer melding".toRegex(),
            )
        }
    }

    @Test
    fun `skal kunne konsumere meldinger og lagre dem i Redis`() {
        val spørreundersøkelseId = UUID.randomUUID()
        kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId)

        runBlocking {
            val result =
                redis.spørreundersøkelseService.hentePågåendeSpørreundersøkelse(spørreundersøkelseId)
            result.id shouldBe spørreundersøkelseId
            result.temaer.forEach {
                it.navn shouldNotBe null
                it.spørsmål shouldNotBe emptyList<Spørsmål>()
            }
        }

        runBlocking {
            val result =
                redis.spørreundersøkelseService.hentePågåendeSpørreundersøkelse(spørreundersøkelseId)
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
