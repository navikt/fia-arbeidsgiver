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
	fun `skal kunne hente spørsmål i en spørreundersøkelse med flere temaer`() {
		val spørreundersøkelseId = UUID.randomUUID()
		val spørreundersøkelseDto = TestContainerHelper.kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId)

		runBlocking {
			val bliMedDTO = fiaArbeidsgiverApi.bliMed(spørreundersøkelseId = spørreundersøkelseId)

			// vert åpner spørsmål i tema 1
			val tema1 = spørreundersøkelseDto.temaMedSpørsmålOgSvaralternativer.first()
			val spørsmålITema1 = tema1.spørsmålOgSvaralternativer.first().id
			fiaArbeidsgiverApi.hentSpørsmålSomVertV2(
				tema = tema1.temanavn,
				spørsmålId = spørsmålITema1,
				spørreundersøkelse = spørreundersøkelseDto
			)

			// vert åpner spørsmål i tema 1
			val tema2 = spørreundersøkelseDto.temaMedSpørsmålOgSvaralternativer.last()
			val spørsmålITema2 = tema2.spørsmålOgSvaralternativer.first().id
			fiaArbeidsgiverApi.hentSpørsmålSomVertV2(
				tema = tema2.temanavn,
				spørsmålId = spørsmålITema2,
				spørreundersøkelse = spørreundersøkelseDto
			)

			fiaArbeidsgiverApi.hentSpørsmålSomDeltaker(
				tema = tema1.temanavn,
				spørsmålId = spørsmålITema1,
				bliMedDTO = bliMedDTO
			) shouldNotBe null

			fiaArbeidsgiverApi.hentSpørsmålSomDeltaker(
				tema = tema2.temanavn,
				spørsmålId = spørsmålITema2,
				bliMedDTO = bliMedDTO
			) shouldNotBe null

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