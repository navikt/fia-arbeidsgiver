package no.nav.fia.arbeidsgiver.sporreundersokelse.api.deltaker

import io.kotest.assertions.shouldFail
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.runBlocking
import no.nav.fia.arbeidsgiver.helper.TestContainerHelper
import no.nav.fia.arbeidsgiver.helper.TestContainerHelper.Companion.fiaArbeidsgiverApi
import no.nav.fia.arbeidsgiver.helper.bliMed
import no.nav.fia.arbeidsgiver.helper.hentFørsteSpørsmål
import no.nav.fia.arbeidsgiver.helper.hentSpørsmålSomDeltaker
import no.nav.fia.arbeidsgiver.helper.hentSpørsmålSomVertV2
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.BliMedDTO
import java.util.*
import kotlin.test.Test

class SpørreundersøkelseDeltakerTest {
	@Test
	fun `skal verifisere sesjonsid for deltakere`() {
		val spørreundersøkelseId = UUID.randomUUID()
		val spørreundersøkelseDto = TestContainerHelper.kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId)

		runBlocking {
			shouldFail {
				fiaArbeidsgiverApi.hentFørsteSpørsmål(bliMedDTO = BliMedDTO(
					spørreundersøkelseId = spørreundersøkelseDto.spørreundersøkelseId,
					sesjonsId = UUID.randomUUID().toString(),
				))
			}
		}
	}

	@Test
	fun `skal kunne finne ut hvilket tema og spørsmål som er det første`() {
		val spørreundersøkelseId = UUID.randomUUID()
		val spørreundersøkelseDto = TestContainerHelper.kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId)

		runBlocking {
			val bliMedDTO = fiaArbeidsgiverApi.bliMed(spørreundersøkelseId = spørreundersøkelseId)
			val startDto = fiaArbeidsgiverApi.hentFørsteSpørsmål(bliMedDTO = bliMedDTO)
			startDto.temaId shouldBe spørreundersøkelseDto.temaMedSpørsmålOgSvaralternativer.first().temanavn
			startDto.spørsmålId shouldBe spørreundersøkelseDto.temaMedSpørsmålOgSvaralternativer.first().spørsmålOgSvaralternativer.first().id
		}
	}

	@Test
	fun `skal kunne få spørsmål når verten har åpnet det`() {
		val spørreundersøkelseId = UUID.randomUUID()
		val spørreundersøkelseDto = TestContainerHelper.kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId)

		runBlocking {
			val bliMedDTO = fiaArbeidsgiverApi.bliMed(spørreundersøkelseId = spørreundersøkelseId)
			val startDto = fiaArbeidsgiverApi.hentFørsteSpørsmål(bliMedDTO = bliMedDTO)

			// -- Vert har ikke åpnet spørsmål ennå
			fiaArbeidsgiverApi.hentSpørsmålSomDeltaker(
				tema = startDto.temaId,
				spørsmålId = startDto.spørsmålId,
				bliMedDTO = bliMedDTO) shouldBe null

			// -- Vert åpner spørsmål
			fiaArbeidsgiverApi.hentSpørsmålSomVertV2(
				tema = startDto.temaId,
				spørsmålId = startDto.spørsmålId,
				spørreundersøkelse = spørreundersøkelseDto
			)
			// --

			val spørsmålsoversiktDto = fiaArbeidsgiverApi.hentSpørsmålSomDeltaker(
				tema = startDto.temaId,
				spørsmålId = startDto.spørsmålId,
				bliMedDTO = bliMedDTO
			)
			spørsmålsoversiktDto shouldNotBe null
		}
	}
}