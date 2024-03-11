package no.nav.fia.arbeidsgiver.sporreundersokelse.kafka

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.inspectors.forAll
import io.kotest.matchers.equals.shouldNotBeEqual
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import no.nav.fia.arbeidsgiver.helper.TestContainerHelper
import no.nav.fia.arbeidsgiver.http.Feil
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.Tema
import java.util.*
import kotlin.test.Test

class SpørreundersøkelseKonsumentTest {
    @Test
    fun `skal kunne konsumere meldinger`() {
        val id = UUID.randomUUID()
        TestContainerHelper.kafka.sendSpørreundersøkelse(spørreundersøkelseId = id)

	    runBlocking {
		    val result = TestContainerHelper.redis.spørreundersøkelseService.hentePågåendeSpørreundersøkelse(id)
		    result.spørreundersøkelseId shouldBe id
	    }
    }

    @Test
    fun `skal kunne konsumere meldinger med ukjente felt`() {
        val id = UUID.randomUUID()
        val spørreundersøkelse =  TestContainerHelper.kafka.enStandardSpørreundersøkelse(id).toJson()
        val spørreundersøkelseMedEkstraFelt =  spørreundersøkelse.replace("\"temanavn\"", "\"ukjentFelt\":\"X\",\"temanavn\"")

        spørreundersøkelseMedEkstraFelt shouldNotBeEqual spørreundersøkelse

        TestContainerHelper.kafka.sendSpørreundersøkelse(spørreundersøkelseId = id, spørreundersøkelsesStreng = spørreundersøkelseMedEkstraFelt)

	    runBlocking {
		    val result = TestContainerHelper.redis.spørreundersøkelseService.hentePågåendeSpørreundersøkelse(id)
		    result.spørreundersøkelseId shouldBe id
	    }
    }

	@Test
	fun `skal håndtere slettede kartlegginger`() {
		val id = UUID.randomUUID()
		TestContainerHelper.kafka.sendSpørreundersøkelse(spørreundersøkelseId = id)

		val spørreundersøkelse = TestContainerHelper.redis.spørreundersøkelseService.henteSpørreundersøkelse(id)
		spørreundersøkelse.spørreundersøkelseId shouldBe id

		TestContainerHelper.kafka.sendSlettemeldingForSpørreundersøkelse(spørreundersøkelseId = id)
		shouldThrow<Feil> {
			TestContainerHelper.redis.spørreundersøkelseService.henteSpørreundersøkelse(id)
		}
		Tema.entries.forAll {
			shouldThrow<Feil> {
				TestContainerHelper.redis.spørreundersøkelseService.hentTemastatus(id, it) shouldBe null
			}
		}

		// -- TODO: gah, hvorfor kaster ikke denne feil?
		TestContainerHelper.redis.spørreundersøkelseService.hentAntallDeltakere(id) shouldBe 0

		// -- TODO: sjekk at sesjoner blir borte
	}

	private fun SpørreundersøkelseDto.toJson() = Json.encodeToString(this)
}