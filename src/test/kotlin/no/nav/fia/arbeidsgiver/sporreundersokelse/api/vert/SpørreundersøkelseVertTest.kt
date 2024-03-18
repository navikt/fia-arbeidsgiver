package no.nav.fia.arbeidsgiver.sporreundersokelse.api.vert

import io.kotest.assertions.shouldFail
import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import no.nav.fia.arbeidsgiver.helper.TestContainerHelper
import no.nav.fia.arbeidsgiver.helper.TestContainerHelper.Companion.fiaArbeidsgiverApi
import no.nav.fia.arbeidsgiver.helper.bliMed
import no.nav.fia.arbeidsgiver.helper.hentSpørsmålSomVertV2
import no.nav.fia.arbeidsgiver.helper.hentTemaoversikt
import no.nav.fia.arbeidsgiver.helper.vertHenterAntallDeltakere
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.vert.dto.TemaOversiktDto
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
                    temaId = it.temanavn.name,
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
            val spørsmålsoversiktDto = fiaArbeidsgiverApi.hentSpørsmålSomVertV2(
                tema = tema.temanavn,
                spørsmålId = spørsmål.id,
                spørreundersøkelse = spørreundersøkelseDto
            )
            spørsmålsoversiktDto.spørsmålTekst shouldBe spørsmål.spørsmål
            spørsmålsoversiktDto.svaralternativer shouldContainInOrder spørsmål.svaralternativer

            spørsmålsoversiktDto.nesteId shouldBe tema.spørsmålOgSvaralternativer[1].id
            spørsmålsoversiktDto.nesteType shouldBe "SPØRSMÅL"
        }

        // på siste spørsmål i tema
        runBlocking {
            val tema = spørreundersøkelseDto.temaMedSpørsmålOgSvaralternativer.first()
            val spørsmål = tema.spørsmålOgSvaralternativer.last()
            val spørsmålsoversiktDto = fiaArbeidsgiverApi.hentSpørsmålSomVertV2(
                tema = tema.temanavn,
                spørsmålId = spørsmål.id,
                spørreundersøkelse = spørreundersøkelseDto
            )
            spørsmålsoversiktDto.spørsmålTekst shouldBe spørsmål.spørsmål
            spørsmålsoversiktDto.svaralternativer shouldContainInOrder spørsmål.svaralternativer

            spørsmålsoversiktDto.nesteId shouldBe null
            spørsmålsoversiktDto.nesteType shouldBe "FERDIG"
        }
    }
}