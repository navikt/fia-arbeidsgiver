package no.nav.fia.arbeidsgiver.sporreundersokelse.api.deltaker

import HEADER_SESJON_ID
import io.kotest.assertions.shouldFail
import io.kotest.inspectors.forAtLeastOne
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.request.header
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import no.nav.fia.arbeidsgiver.helper.TestContainerHelper.Companion.fiaArbeidsgiverApi
import no.nav.fia.arbeidsgiver.helper.TestContainerHelper.Companion.kafka
import no.nav.fia.arbeidsgiver.helper.TestContainerHelper.Companion.shouldContainLog
import no.nav.fia.arbeidsgiver.helper.bliMed
import no.nav.fia.arbeidsgiver.helper.hentFørsteSpørsmål
import no.nav.fia.arbeidsgiver.helper.hentSpørsmålSomDeltaker
import no.nav.fia.arbeidsgiver.helper.performGet
import no.nav.fia.arbeidsgiver.helper.svarPåSpørsmål
import no.nav.fia.arbeidsgiver.konfigurasjon.KafkaTopics
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.BliMedDTO
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.IdentifiserbartSpørsmål
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

			shouldFail {
				val spørsmål = spørreundersøkelseDto.hentSpørsmålITema(startDto)
				fiaArbeidsgiverApi.svarPåSpørsmål(
					spørsmål = startDto,
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
	fun `deltaker får NOT_FOUND dersom spørsmål er ikke funnet`() {
		val spørreundersøkelseId = UUID.randomUUID()
		kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId)

		runBlocking {
			val bliMedDTO = fiaArbeidsgiverApi.bliMed(spørreundersøkelseId = spørreundersøkelseId)

			fiaArbeidsgiverApi.performGet(
				url = "$DELTAKER_BASEPATH/$spørreundersøkelseId/${Tema.UTVIKLE_PARTSSAMARBEID}/${UUID.randomUUID()}"
			) {
				header(HEADER_SESJON_ID, bliMedDTO.sesjonsId)
			}.status shouldBe HttpStatusCode.NotFound
		}
	}

	@Test
	fun `skal kunne hente spørsmål i en spørreundersøkelse med flere temaer`() {
		val spørreundersøkelseId = UUID.randomUUID()
		val spørreundersøkelseDto = kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId)

		runBlocking {
			val bliMedDTO = fiaArbeidsgiverApi.bliMed(spørreundersøkelseId = spørreundersøkelseId)
			val tema1 = spørreundersøkelseDto.temaMedSpørsmålOgSvaralternativer.first()
			val spørsmålITema1 = tema1.spørsmålOgSvaralternativer.first().id
			spørreundersøkelseDto.åpneSpørsmålOgHentSomDeltaker(
				spørsmål = IdentifiserbartSpørsmål(tema = tema1.temanavn,spørsmålId = spørsmålITema1),
				bliMedDTO = bliMedDTO
			) shouldNotBe null

			val tema2 = spørreundersøkelseDto.temaMedSpørsmålOgSvaralternativer.last()
			val spørsmålITema2 = tema2.spørsmålOgSvaralternativer.first().id
			spørreundersøkelseDto.åpneSpørsmålOgHentSomDeltaker(
				spørsmål = IdentifiserbartSpørsmål(tema = tema2.temanavn, spørsmålId = spørsmålITema2),
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
				spørsmål = startDto,
				bliMedDTO = bliMedDTO
			)

			spørsmål.nesteSpørsmål?.spørsmålId shouldBe spørreundersøkelseDto.nesteSpørsmålITema(
				temanavn = startDto.tema,
				spørsmålId = startDto.spørsmålId
			)?.id

			// -- Test at "nesteSpørsmål" for siste spørsmål i ett tema peker på første spørsmål i neste tema
			val sisteSpørsmålITema = spørreundersøkelseDto.åpneSpørsmålOgHentSomDeltaker(
				spørsmål = IdentifiserbartSpørsmål(
					tema = startDto.tema,
					spørsmålId = spørreundersøkelseDto.temaMedSpørsmålOgSvaralternativer
						.first().spørsmålOgSvaralternativer.last().id
				),
				bliMedDTO = bliMedDTO
			)
			sisteSpørsmålITema.nesteSpørsmål?.tema shouldBe
					spørreundersøkelseDto.temaMedSpørsmålOgSvaralternativer[1].temanavn
			sisteSpørsmålITema.nesteSpørsmål?.spørsmålId shouldBe
					spørreundersøkelseDto.temaMedSpørsmålOgSvaralternativer[1].spørsmålOgSvaralternativer.first().id
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
				spørsmål = startDto,
				bliMedDTO = bliMedDTO) shouldBe null

			val spørsmålsoversiktDto = spørreundersøkelseDto.åpneSpørsmålOgHentSomDeltaker(
				spørsmål = startDto,
				bliMedDTO = bliMedDTO
			)
			spørsmålsoversiktDto.spørsmålTekst shouldBe spørreundersøkelseDto.hentSpørsmålITema(startDto).spørsmål
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
			val spørsmål = spørreundersøkelseDto.hentSpørsmålITema(startDto)
			fiaArbeidsgiverApi.svarPåSpørsmål(
				spørsmål = startDto,
				svarId = spørsmål.svaralternativer.first().svarId,
				bliMedDTO = bliMedDTO
			)

			kafka.ventOgKonsumerKafkaMeldinger(
				key = "${bliMedDTO.sesjonsId}_${spørsmål.id}",
				konsument = spørreundersøkelseSvarKonsument
			) { meldinger ->
				val deserialiserteSvar = meldinger.map {
					Json.decodeFromString<SpørreundersøkelseSvarDTO>(it)
				}
				deserialiserteSvar shouldHaveAtLeastSize 1
				deserialiserteSvar.forAtLeastOne { svar ->
					svar.spørreundersøkelseId shouldBe spørreundersøkelseId.toString()
					svar.sesjonId shouldBe bliMedDTO.sesjonsId
					svar.spørsmålId shouldBe spørsmål.id
					svar.svarId shouldBe spørsmål.svaralternativer.first().svarId
				}
			}
		}
	}

	@Test
	fun `skal ikke kunne svare på en ukjent spørreundersøkelse`() {
		val spørreundersøkelseId = UUID.randomUUID()
		val spørreundersøkelseDto = kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId)

		runBlocking {
			val bliMedDTO = fiaArbeidsgiverApi.bliMed(spørreundersøkelseId = spørreundersøkelseId)
			val startDto = fiaArbeidsgiverApi.hentFørsteSpørsmål(bliMedDTO = bliMedDTO)
			shouldFail {
				fiaArbeidsgiverApi.svarPåSpørsmål(
					spørsmål = startDto,
					svarId = spørreundersøkelseDto.hentSpørsmålITema(startDto).svaralternativer.first().svarId,
					bliMedDTO = bliMedDTO.copy(spørreundersøkelseId = UUID.randomUUID().toString())
				)
			}
			// -- tester på "Ugyldig sesjonsId", da vi verifiserer sesjonsId mot spørreundersøkelsesId
			fiaArbeidsgiverApi shouldContainLog "Ugyldig sesjonId".toRegex()
		}
	}

	@Test
	fun `skal ikke kunne svare med ukjent svarId`() {
		val spørreundersøkelseId = UUID.randomUUID()
		kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId)

		runBlocking {
			val bliMedDTO = fiaArbeidsgiverApi.bliMed(spørreundersøkelseId = spørreundersøkelseId)
			val startDto = fiaArbeidsgiverApi.hentFørsteSpørsmål(bliMedDTO = bliMedDTO)
			val svarId = UUID.randomUUID().toString()
			shouldFail {
				fiaArbeidsgiverApi.svarPåSpørsmål(
					spørsmål = startDto,
					svarId = svarId,
					bliMedDTO = bliMedDTO
				)
			}
			fiaArbeidsgiverApi shouldContainLog "Ukjent svar \\($svarId\\)".toRegex()
		}
	}
}

private suspend fun SpørreundersøkelseDto.åpneSpørsmålOgHentSomDeltaker(
	spørsmål: IdentifiserbartSpørsmål,
	bliMedDTO: BliMedDTO
) = åpneSpørsmål(spørsmål).let {
	val spørsmålsoversiktDto = fiaArbeidsgiverApi.hentSpørsmålSomDeltaker(spørsmål = spørsmål, bliMedDTO = bliMedDTO)
	assertNotNull(spørsmålsoversiktDto)
	spørsmålsoversiktDto
}

private fun SpørreundersøkelseDto.hentSpørsmålITema(spørsmål: IdentifiserbartSpørsmål) =
	temaMedSpørsmålOgSvaralternativer.firstOrNull { it.temanavn == spørsmål.tema }?.let { tema ->
		val spørsmålIdx = tema.spørsmålOgSvaralternativer.indexOfFirst { it.id == spørsmål.spørsmålId }
		tema.spørsmålOgSvaralternativer.elementAtOrNull(spørsmålIdx)
	}.let {
		assertNotNull(it)
		it
	}

private fun SpørreundersøkelseDto.nesteSpørsmålITema(temanavn: Tema, spørsmålId: String) =
	temaMedSpørsmålOgSvaralternativer.firstOrNull { it.temanavn == temanavn }?.let { tema ->
		val spørsmålIdx = tema.spørsmålOgSvaralternativer.indexOfFirst { it.id == spørsmålId }
		tema.spørsmålOgSvaralternativer.elementAtOrNull(spørsmålIdx + 1)
	}
