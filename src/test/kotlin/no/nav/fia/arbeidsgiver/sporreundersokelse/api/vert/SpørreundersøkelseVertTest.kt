package no.nav.fia.arbeidsgiver.sporreundersokelse.api.vert

import io.kotest.assertions.shouldFail
import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.runBlocking
import no.nav.fia.arbeidsgiver.helper.*
import no.nav.fia.arbeidsgiver.helper.TestContainerHelper.Companion.fiaArbeidsgiverApi
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.deltaker.hentSpørsmålITema
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.IdentifiserbartSpørsmål
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.vert.dto.TemaOversiktDto
import no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.dto.SpørreundersøkelseDto
import java.util.*
import kotlin.test.Test

class SpørreundersøkelseVertTest {

    val TEMA_ID_FOR_REDUSERE_SYKEFRAVÆR = 1

    @Test
    fun `vertssider skal ikke kunne hentes uten gyldig vertsId`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val spørreundersøkelseDto =
            TestContainerHelper.kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId)

        runBlocking {
            fiaArbeidsgiverApi.vertHenterAntallDeltakere(
                vertId = spørreundersøkelseDto.vertId,
                spørreundersøkelseId = spørreundersøkelseDto.spørreundersøkelseId
            ) shouldBe 0

            shouldFail {
                fiaArbeidsgiverApi.vertHenterAntallDeltakere(
                    vertId = UUID.randomUUID().toString(),
                    spørreundersøkelseId = spørreundersøkelseDto.spørreundersøkelseId
                )
            }
        }
    }

    @Test
    fun `vert skal kunne hente antall deltakere i en undersøkelse`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val spørreundersøkelseDto =
            TestContainerHelper.kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId)

        runBlocking {
            fiaArbeidsgiverApi.vertHenterAntallDeltakere(
                vertId = spørreundersøkelseDto.vertId,
                spørreundersøkelseId = spørreundersøkelseDto.spørreundersøkelseId
            ) shouldBe 0

            val antallDeltakere = 5
            for (deltaker in 1..5)
                fiaArbeidsgiverApi.bliMed(spørreundersøkelseId = spørreundersøkelseId)

            fiaArbeidsgiverApi.vertHenterAntallDeltakere(
                vertId = spørreundersøkelseDto.vertId,
                spørreundersøkelseId = spørreundersøkelseDto.spørreundersøkelseId
            ) shouldBe antallDeltakere
        }
    }

    @Test
    fun `vert skal kunne få ut oversikt over alle temaer i en spørreundersøkelse`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val spørreundersøkelseDto =
            TestContainerHelper.kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId)

        runBlocking {
            val temaOversikt = fiaArbeidsgiverApi.hentTemaoversikt(spørreundersøkelseDto)
            temaOversikt shouldHaveSize spørreundersøkelseDto.temaMedSpørsmålOgSvaralternativer.size
            temaOversikt shouldContainInOrder spørreundersøkelseDto.temaMedSpørsmålOgSvaralternativer.mapIndexed { index, it ->
                TemaOversiktDto(
                    temaId = it.temaId,
                    tittel = it.temanavn.name,
                    del = index + 1,
                    temanavn = it.temanavn,
                    beskrivelse = it.beskrivelse,
                    introtekst = it.introtekst,
                    førsteSpørsmålId = it.spørsmålOgSvaralternativer.first().id
                )
            }
        }
    }

    @Test
    fun `vert skal kunne få ut oversikt over ett tema i en spørreundersøkelse`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val spørreundersøkelseDto =
            TestContainerHelper.kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId)

        val temaRedusereSykefravær =
            spørreundersøkelseDto.temaMedSpørsmålOgSvaralternativer.first { it.temaId == TEMA_ID_FOR_REDUSERE_SYKEFRAVÆR }

        runBlocking {
            val temaOversikt = fiaArbeidsgiverApi.hentTemaoversiktForEttTema(
                spørreundersøkelse = spørreundersøkelseDto,
                temaId = TEMA_ID_FOR_REDUSERE_SYKEFRAVÆR
            )
            temaOversikt shouldNotBe null
            temaOversikt.temanavn shouldBe temaRedusereSykefravær.temanavn
            temaOversikt.del shouldBe 2
            temaOversikt.beskrivelse shouldBe temaRedusereSykefravær.beskrivelse
            temaOversikt.introtekst shouldBe temaRedusereSykefravær.introtekst
            temaOversikt.førsteSpørsmålId shouldBe temaRedusereSykefravær.spørsmålOgSvaralternativer.first().id
        }
    }

    @Test
    fun `vert skal kunne hente spørsmålsoversikt`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val spørreundersøkelseDto =
            TestContainerHelper.kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId)

        runBlocking {
            val tema = spørreundersøkelseDto.temaMedSpørsmålOgSvaralternativer.first()
            val spørsmål = tema.spørsmålOgSvaralternativer.first()
            val spørsmålsoversiktDto = spørreundersøkelseDto.åpneSpørsmål(
                spørsmål = IdentifiserbartSpørsmål(
                    temaId = tema.temaId,
                    spørsmålId = spørsmål.id
                )
            )
            spørsmålsoversiktDto.spørsmålTekst shouldBe spørsmål.spørsmål
            spørsmålsoversiktDto.svaralternativer shouldContainInOrder spørsmål.svaralternativer
            spørsmålsoversiktDto.nesteSpørsmål?.spørsmålId shouldBe tema.spørsmålOgSvaralternativer[1].id
            spørsmålsoversiktDto.nesteSpørsmål?.temaId shouldBe tema.temaId
            spørsmålsoversiktDto.temanummer shouldBe 1
            spørsmålsoversiktDto.antallTema shouldBe spørreundersøkelseDto.temaMedSpørsmålOgSvaralternativer.size
            spørsmålsoversiktDto.spørsmålnummer shouldBe 1
            spørsmålsoversiktDto.antallSpørsmål shouldBe tema.spørsmålOgSvaralternativer.size
        }

        // på siste spørsmål
        runBlocking {
            val tema = spørreundersøkelseDto.temaMedSpørsmålOgSvaralternativer.last()
            val spørsmål = tema.spørsmålOgSvaralternativer.last()
            val spørsmålsoversiktDto = spørreundersøkelseDto.åpneSpørsmål(
                spørsmål = IdentifiserbartSpørsmål(
                    temaId = tema.temaId,
                    spørsmålId = spørsmål.id
                )
            )
            spørsmålsoversiktDto.spørsmålTekst shouldBe spørsmål.spørsmål
            spørsmålsoversiktDto.svaralternativer shouldContainInOrder spørsmål.svaralternativer
            spørsmålsoversiktDto.nesteSpørsmål shouldBe null
        }
    }

    @Test
    fun `vert skal kunne vite hvor mange som har svart på ett spørsmål`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val spørreundersøkelseDto =
            TestContainerHelper.kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId)

        runBlocking {
            val førsteSpørsmål = IdentifiserbartSpørsmål(
                temaId = spørreundersøkelseDto.temaMedSpørsmålOgSvaralternativer.first().temaId,
                spørsmålId = spørreundersøkelseDto.temaMedSpørsmålOgSvaralternativer.first().spørsmålOgSvaralternativer.first().id
            )

            spørreundersøkelseDto.åpneSpørsmål(spørsmål = førsteSpørsmål)
            fiaArbeidsgiverApi.hentAntallSvarForSpørsmål(
                spørsmål = førsteSpørsmål,
                spørreundersøkelse = spørreundersøkelseDto
            ) shouldBe 0

            (1..5).forEach { antallSvar ->
                val bliMedDTO = fiaArbeidsgiverApi.bliMed(spørreundersøkelseId = spørreundersøkelseId)
                fiaArbeidsgiverApi.svarPåSpørsmål(
                    spørsmål = førsteSpørsmål,
                    svarId = spørreundersøkelseDto.hentSpørsmålITema(førsteSpørsmål).svaralternativer.first().svarId,
                    bliMedDTO = bliMedDTO,
                ) {
                    TestContainerHelper.kafka.sendAntallSvar(
                        spørreundersøkelseId = spørreundersøkelseId.toString(),
                        spørsmålId = førsteSpørsmål.spørsmålId,
                        antallSvar = antallSvar
                    )
                }

                fiaArbeidsgiverApi.hentAntallSvarForSpørsmål(
                    spørsmål = førsteSpørsmål,
                    spørreundersøkelse = spørreundersøkelseDto
                ) shouldBe antallSvar
            }
        }
    }
}

suspend fun SpørreundersøkelseDto.åpneSpørsmål(
    spørsmål: IdentifiserbartSpørsmål,
) = fiaArbeidsgiverApi.hentSpørsmålSomVertV2(
    spørsmål = spørsmål,
    spørreundersøkelse = this
)
