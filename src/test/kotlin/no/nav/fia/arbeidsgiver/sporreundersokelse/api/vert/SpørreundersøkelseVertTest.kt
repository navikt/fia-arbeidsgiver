package no.nav.fia.arbeidsgiver.sporreundersokelse.api.vert

import io.kotest.assertions.shouldFail
import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import no.nav.fia.arbeidsgiver.helper.TestContainerHelper
import no.nav.fia.arbeidsgiver.helper.TestContainerHelper.Companion.fiaArbeidsgiverApi
import no.nav.fia.arbeidsgiver.helper.bliMed
import no.nav.fia.arbeidsgiver.helper.hentAntallSvarForSpørsmål
import no.nav.fia.arbeidsgiver.helper.hentSpørsmålSomVertV2
import no.nav.fia.arbeidsgiver.helper.hentTemaoversikt
import no.nav.fia.arbeidsgiver.helper.svarPåSpørsmål
import no.nav.fia.arbeidsgiver.helper.vertHenterAntallDeltakere
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.deltaker.hentSpørsmålITema
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.IdentifiserbartSpørsmål
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.vert.dto.TemaOversiktDto
import no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.dto.SpørreundersøkelseDto
import java.util.*
import kotlin.test.Test

class SpørreundersøkelseVertTest {
    @Test
    fun `vertssider skal ikke kunne hentes uten gyldig vertsId`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val spørreundersøkelseDto = TestContainerHelper.kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId)

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
        val spørreundersøkelseDto = TestContainerHelper.kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId)

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
        val spørreundersøkelseDto = TestContainerHelper.kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId)

        runBlocking {
            val temaOversikt = fiaArbeidsgiverApi.hentTemaoversikt(spørreundersøkelseDto)
            temaOversikt shouldHaveSize spørreundersøkelseDto.temaMedSpørsmålOgSvaralternativer.size
            temaOversikt shouldContainInOrder spørreundersøkelseDto.temaMedSpørsmålOgSvaralternativer.map {
                TemaOversiktDto(
                    tittel = it.temanavn.name,
                    temaId = it.temanavn,
                    beskrivelse = it.beskrivelse,
                    introtekst = it.introtekst,
                    førsteSpørsmålId = it.spørsmålOgSvaralternativer.first().id
                )
            }
        }
    }

    @Test
    fun `vert skal kunne hente spørsmålsoversikt`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val spørreundersøkelseDto = TestContainerHelper.kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId)

        // har flere spørsmål i tema
        runBlocking {
            val tema = spørreundersøkelseDto.temaMedSpørsmålOgSvaralternativer.first()
            val spørsmål = tema.spørsmålOgSvaralternativer.first()
            val spørsmålsoversiktDto = spørreundersøkelseDto.åpneSpørsmål(
                spørsmål = IdentifiserbartSpørsmål(
                    tema = tema.temanavn,
                    spørsmålId = spørsmål.id
                )
            )
            spørsmålsoversiktDto.spørsmålTekst shouldBe spørsmål.spørsmål
            spørsmålsoversiktDto.svaralternativer shouldContainInOrder spørsmål.svaralternativer
            spørsmålsoversiktDto.nesteSpørsmål?.spørsmålId shouldBe tema.spørsmålOgSvaralternativer[1].id
            spørsmålsoversiktDto.nesteSpørsmål?.tema shouldBe tema.temanavn

        }

        // på siste spørsmål
        runBlocking {
            val tema = spørreundersøkelseDto.temaMedSpørsmålOgSvaralternativer.last()
            val spørsmål = tema.spørsmålOgSvaralternativer.last()
            val spørsmålsoversiktDto = spørreundersøkelseDto.åpneSpørsmål(
                spørsmål = IdentifiserbartSpørsmål(
                    tema = tema.temanavn,
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
        val spørreundersøkelseDto = TestContainerHelper.kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId)

        runBlocking {
            val temaOversiktListe = fiaArbeidsgiverApi.hentTemaoversikt(spørreundersøkelseDto)
            val førsteSpørsmål = temaOversiktListe.first().tilIdentifiserbartSpørsmål()
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
    spørsmål: IdentifiserbartSpørsmål
) = fiaArbeidsgiverApi.hentSpørsmålSomVertV2(
    spørsmål = spørsmål,
    spørreundersøkelse = this
)
