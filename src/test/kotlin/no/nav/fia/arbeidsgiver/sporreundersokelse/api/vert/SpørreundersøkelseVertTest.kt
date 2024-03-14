package no.nav.fia.arbeidsgiver.sporreundersokelse.api.vert

import io.kotest.assertions.shouldFail
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import no.nav.fia.arbeidsgiver.helper.TestContainerHelper
import no.nav.fia.arbeidsgiver.helper.TestContainerHelper.Companion.fiaArbeidsgiverApi
import no.nav.fia.arbeidsgiver.helper.vertHenterAntallDeltakere
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
}