package no.nav.fia.arbeidsgiver.sporreundersokelse.api.deltaker

import io.kotest.assertions.shouldFail
import io.kotest.inspectors.forAtLeastOne
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import no.nav.fia.arbeidsgiver.helper.TestContainerHelper.Companion.fiaArbeidsgiverApi
import no.nav.fia.arbeidsgiver.helper.TestContainerHelper.Companion.kafka
import no.nav.fia.arbeidsgiver.helper.bliMed
import no.nav.fia.arbeidsgiver.helper.hentFørsteSpørsmål
import no.nav.fia.arbeidsgiver.helper.hentSpørsmålSomDeltaker
import no.nav.fia.arbeidsgiver.helper.hentSpørsmålSomVertV2
import no.nav.fia.arbeidsgiver.helper.svarPåSpørsmål
import no.nav.fia.arbeidsgiver.konfigurasjon.KafkaTopics
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.BliMedDTO
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.vert.åpneSpørsmål
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.SpørreundersøkelseStatus
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.Tema
import no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.dto.SpørreundersøkelseDto
import no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.dto.SpørreundersøkelseSvarDTO
import org.junit.After
import org.junit.Before
import java.util.*
import kotlin.test.Test
import kotlin.test.assertNotNull

class SpørreundersøkelseDeltakerTest {
	private val spørreundersøkelseSvarKonsument =
		kafka.nyKonsument(topic = KafkaTopics.SPØRREUNDERSØKELSE_SVAR)

	@Before
	fun setUp() {
		spørreundersøkelseSvarKonsument.subscribe(mutableListOf(KafkaTopics.SPØRREUNDERSØKELSE_SVAR.navnMedNamespace))
	}

	@After
	fun tearDown() {
		spørreundersøkelseSvarKonsument.unsubscribe()
		spørreundersøkelseSvarKonsument.close()
	}

	@Test
	fun `skal verifisere sesjonsid for deltakere`() {
		val spørreundersøkelseId = UUID.randomUUID()
		val spørreundersøkelseDto = kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId)

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
	fun `skal ikke kunne bli med i en spørreundersøkelse som ikke er påbegynt`() {
		val spørreundersøkelseId = UUID.randomUUID()
		kafka.sendSpørreundersøkelse(
			spørreundersøkelseId = spørreundersøkelseId,
			spørreundersøkelsesStreng = Json.encodeToString<SpørreundersøkelseDto>(
				kafka.enStandardSpørreundersøkelse(
					spørreundersøkelseId = spørreundersøkelseId,
					spørreundersøkelseStatus = SpørreundersøkelseStatus.OPPRETTET
				)
			)
		)

		runBlocking {
			shouldFail {
				fiaArbeidsgiverApi.bliMed(spørreundersøkelseId)
			}
		}
	}

	@Test
	fun `skal ikke kunne svare på spørsmål etter at en undersøkelse er avsluttet`() {
		val spørreundersøkelseId = UUID.randomUUID()
		val spørreundersøkelseDto = kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId)

		runBlocking {
			val bliMedDTO = fiaArbeidsgiverApi.bliMed(spørreundersøkelseId = spørreundersøkelseId)
			val startDto = fiaArbeidsgiverApi.hentFørsteSpørsmål(bliMedDTO = bliMedDTO)

			kafka.sendSpørreundersøkelse(
				spørreundersøkelseId = spørreundersøkelseId,
				spørreundersøkelsesStreng = Json.encodeToString<SpørreundersøkelseDto>(
					spørreundersøkelseDto.copy(status = SpørreundersøkelseStatus.AVSLUTTET)
				)
			)

			val spørsmål =
				spørreundersøkelseDto.hentSpørsmålITema(temanavn = startDto.tema, spørsmålId = startDto.spørsmålId)
			assertNotNull(spørsmål)

			shouldFail {
				fiaArbeidsgiverApi.svarPåSpørsmål(
					tema = startDto.tema,
					spørsmålId = startDto.spørsmålId,
					svarId = spørsmål.svaralternativer.first().svarId,
					bliMedDTO = bliMedDTO
				)
			}
		}
	}

	@Test
	fun `skal kunne finne ut hvilket tema og spørsmål som er det første`() {
		val spørreundersøkelseId = UUID.randomUUID()
		val spørreundersøkelseDto = kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId)

		runBlocking {
			val bliMedDTO = fiaArbeidsgiverApi.bliMed(spørreundersøkelseId = spørreundersøkelseId)
			val startDto = fiaArbeidsgiverApi.hentFørsteSpørsmål(bliMedDTO = bliMedDTO)
			startDto.tema shouldBe spørreundersøkelseDto.temaMedSpørsmålOgSvaralternativer.first().temanavn
			startDto.spørsmålId shouldBe spørreundersøkelseDto.temaMedSpørsmålOgSvaralternativer.first().spørsmålOgSvaralternativer.first().id
		}
	}

	@Test
	fun `skal kunne hente spørsmål i en spørreundersøkelse med flere temaer`() {
		val spørreundersøkelseId = UUID.randomUUID()
		val spørreundersøkelseDto = kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId)

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
	fun `deltaker skal få neste spørsmålId når hen henter et spørsmål`() {
		val spørreundersøkelseId = UUID.randomUUID()
		val spørreundersøkelseDto = kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId)

		runBlocking {
			val bliMedDTO = fiaArbeidsgiverApi.bliMed(spørreundersøkelseId = spørreundersøkelseId)
			val startDto = fiaArbeidsgiverApi.hentFørsteSpørsmål(bliMedDTO = bliMedDTO)

			val spørsmål = spørreundersøkelseDto.åpneSpørsmålOgHentSomDeltaker(
				tema = startDto.tema,
				spørsmålId = startDto.spørsmålId,
				bliMedDTO = bliMedDTO
			)

			spørsmål.nesteSpørsmål?.spørsmålId shouldBe spørreundersøkelseDto.nesteSpørsmålITema(
				temanavn = startDto.tema,
				spørsmålId = startDto.spørsmålId
			)?.id
		}
	}

	@Test
	fun `skal kunne få spørsmål først når verten har åpnet spørsmålet`() {
		val spørreundersøkelseId = UUID.randomUUID()
		val spørreundersøkelseDto = kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId)

		runBlocking {
			val bliMedDTO = fiaArbeidsgiverApi.bliMed(spørreundersøkelseId = spørreundersøkelseId)
			val startDto = fiaArbeidsgiverApi.hentFørsteSpørsmål(bliMedDTO = bliMedDTO)

			// -- Vert har ikke åpnet spørsmål ennå
			fiaArbeidsgiverApi.hentSpørsmålSomDeltaker(
				tema = startDto.tema,
				spørsmålId = startDto.spørsmålId,
				bliMedDTO = bliMedDTO) shouldBe null

			val spørsmålsoversiktDto = spørreundersøkelseDto.åpneSpørsmålOgHentSomDeltaker(
				tema = startDto.tema,
				spørsmålId = startDto.spørsmålId,
				bliMedDTO = bliMedDTO
			)
			spørsmålsoversiktDto.spørsmålTekst shouldBe spørreundersøkelseDto.hentSpørsmålITema(
				temanavn = startDto.tema,
				spørsmålId = startDto.spørsmålId
			)?.spørsmål
		}
	}

	@Test
	fun `deltaker skal kunne svare på ett spørsmål`() {
		val spørreundersøkelseId = UUID.randomUUID()
		val spørreundersøkelseDto =
			kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId)

		runBlocking {
			val bliMedDTO = fiaArbeidsgiverApi.bliMed(spørreundersøkelseId = spørreundersøkelseId)
			val startDto = fiaArbeidsgiverApi.hentFørsteSpørsmål(bliMedDTO = bliMedDTO)
			val spørsmål =
				spørreundersøkelseDto.hentSpørsmålITema(temanavn = startDto.tema, spørsmålId = startDto.spørsmålId)
			assertNotNull(spørsmål)
			fiaArbeidsgiverApi.svarPåSpørsmål(
				tema = startDto.tema,
				spørsmålId = startDto.spørsmålId,
				svarId = spørsmål.svaralternativer.first().svarId,
				bliMedDTO = bliMedDTO
			)

			kafka.ventOgKonsumerKafkaMeldinger(
				key = "${bliMedDTO.sesjonsId}_${spørsmål.id}",
				konsument = spørreundersøkelseSvarKonsument
			) { meldinger ->
				val objektene = meldinger.map {
					Json.decodeFromString<SpørreundersøkelseSvarDTO>(it)
				}
				objektene shouldHaveAtLeastSize 1
				objektene.forAtLeastOne {
					it.spørreundersøkelseId shouldBe spørreundersøkelseId.toString()
					it.sesjonId shouldBe bliMedDTO.sesjonsId
					it.spørsmålId shouldBe spørsmål.id
					it.svarId shouldBe spørsmål.svaralternativer.first().svarId
				}
			}
		}
	}
}

private suspend fun SpørreundersøkelseDto.åpneSpørsmålOgHentSomDeltaker(
	tema: Tema,
	spørsmålId: String,
	bliMedDTO: BliMedDTO
) = åpneSpørsmål(tema = tema, spørsmålId = spørsmålId).let {
	val spørsmål = fiaArbeidsgiverApi.hentSpørsmålSomDeltaker(
		tema, spørsmålId, bliMedDTO
	)
	assertNotNull(spørsmål)
	spørsmål
}

private fun SpørreundersøkelseDto.hentSpørsmålITema(temanavn: Tema, spørsmålId: String) =
	temaMedSpørsmålOgSvaralternativer.firstOrNull { it.temanavn == temanavn }?.let { tema ->
		val spørsmålIdx = tema.spørsmålOgSvaralternativer.indexOfFirst { it.id == spørsmålId }
		tema.spørsmålOgSvaralternativer.elementAtOrNull(spørsmålIdx)
	}

private fun SpørreundersøkelseDto.nesteSpørsmålITema(temanavn: Tema, spørsmålId: String) =
	temaMedSpørsmålOgSvaralternativer.firstOrNull { it.temanavn == temanavn }?.let { tema ->
		val spørsmålIdx = tema.spørsmålOgSvaralternativer.indexOfFirst { it.id == spørsmålId }
		tema.spørsmålOgSvaralternativer.elementAtOrNull(spørsmålIdx + 1)
	}
