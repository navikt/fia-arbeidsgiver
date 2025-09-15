package no.nav.fia.arbeidsgiver.organisasjoner.api

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import no.nav.fia.arbeidsgiver.helper.AltinnTilgangerContainerHelper.Companion.ALTINN_ORGNR_1
import no.nav.fia.arbeidsgiver.helper.AltinnTilgangerContainerHelper.Companion.ALTINN_ORGNR_2
import no.nav.fia.arbeidsgiver.helper.AltinnTilgangerContainerHelper.Companion.ALTINN_OVERORDNET_ENHET
import no.nav.fia.arbeidsgiver.helper.AltinnTilgangerContainerHelper.OrgnrMedEnkeltrettigheter
import no.nav.fia.arbeidsgiver.helper.TestContainerHelper
import no.nav.fia.arbeidsgiver.helper.TestContainerHelper.Companion.altinnTilgangerContainerHelper
import no.nav.fia.arbeidsgiver.helper.withTokenXToken
import no.nav.fia.arbeidsgiver.helper.withoutGyldigTokenXToken
import no.nav.fia.arbeidsgiver.samarbeidsstatus.api.AltinnTilgangerService.AltinnTilgang
import no.nav.fia.arbeidsgiver.samarbeidsstatus.api.AltinnTilgangerService.Companion.ENKELRETTIGHET_FOREBYGGE_FRAVÆR_IA_SAMARBEID
import no.nav.fia.arbeidsgiver.samarbeidsstatus.api.AltinnTilgangerService.Companion.ENKELRETTIGHET_FOREBYGGE_FRAVÆR_SYKEFRAVÆRSSTATISTIKK
import kotlin.test.BeforeTest
import kotlin.test.Test

class OrganisasjonerEndepunktTest {
    @BeforeTest
    fun setup() {
        runBlocking {
            altinnTilgangerContainerHelper.slettAlleRettigheter()
        }
    }

    @Test
    fun `Uinnlogget bruker får en 401 - Not Authorized i response`() {
        runBlocking {
            val response = TestContainerHelper.hentOrganisasjonerTilgangResponse(
                config = withoutGyldigTokenXToken(),
            )

            response.status.value shouldBe 401
        }
    }

    @Test
    fun `Innlogget bruker som ikke har noen rettigheter får en tom liste`() {
        runBlocking {
            val response = TestContainerHelper.hentOrganisasjonerTilgangResponse(
                config = withTokenXToken(),
            )

            response.status.value shouldBe 200
            val altinnOrganisasjoner = Json.decodeFromString<List<AltinnTilgang>>(response.bodyAsText())
            altinnOrganisasjoner shouldNotBe null
            altinnOrganisasjoner.size shouldBe 0
        }
    }

    @Test
    fun `Innlogget bruker får hente organisasjoner hen har tilgang til`() {
        altinnTilgangerContainerHelper.leggTilRettigheter(
            orgnrTilOverordnetEnhet = ALTINN_OVERORDNET_ENHET,
            underenheterMedRettighet = listOf(
                OrgnrMedEnkeltrettigheter(
                    orgnr = ALTINN_ORGNR_1,
                    altinn3Rettigheter = listOf(ENKELRETTIGHET_FOREBYGGE_FRAVÆR_IA_SAMARBEID),
                ),
                OrgnrMedEnkeltrettigheter(
                    orgnr = ALTINN_ORGNR_2,
                    altinn3Rettigheter = listOf(
                        ENKELRETTIGHET_FOREBYGGE_FRAVÆR_IA_SAMARBEID,
                        ENKELRETTIGHET_FOREBYGGE_FRAVÆR_SYKEFRAVÆRSSTATISTIKK,
                    ),
                ),
            ),
        )

        runBlocking {
            val response = TestContainerHelper.hentOrganisasjonerTilgangResponse(
                config = withTokenXToken(),
            )

            response.status.value shouldBe 200
            val altinnOrganisasjoner = Json.decodeFromString<List<AltinnTilgang>>(response.bodyAsText())
            altinnOrganisasjoner shouldNotBe null
            altinnOrganisasjoner.size shouldBe 1
            val altinnOrganisasjonForOverordnetEnhet: AltinnTilgang =
                altinnOrganisasjoner.find { it.orgnr == ALTINN_OVERORDNET_ENHET }!!
            altinnOrganisasjonForOverordnetEnhet.navn shouldBe "NAVN TIL OVERORDNET ENHET"
            altinnOrganisasjonForOverordnetEnhet.altinn3Tilganger.size shouldBe 0
            altinnOrganisasjonForOverordnetEnhet.erSlettet shouldBe false
            altinnOrganisasjonForOverordnetEnhet.underenheter.size shouldBe 2

            val altinnOrganisasjonForUnderenhet1: AltinnTilgang =
                altinnOrganisasjonForOverordnetEnhet.underenheter.find { it.orgnr == ALTINN_ORGNR_1 }!!
            altinnOrganisasjonForUnderenhet1.navn shouldBe "NAVN TIL UNDERENHET"
            altinnOrganisasjonForUnderenhet1.altinn3Tilganger.size shouldBe 1
            altinnOrganisasjonForUnderenhet1.erSlettet shouldBe false
            altinnOrganisasjonForUnderenhet1.underenheter.size shouldBe 0

            val altinnOrganisasjonForUnderenhet2: AltinnTilgang =
                altinnOrganisasjonForOverordnetEnhet.underenheter.find { it.orgnr == ALTINN_ORGNR_2 }!!
            altinnOrganisasjonForUnderenhet2.navn shouldBe "NAVN TIL UNDERENHET"
            altinnOrganisasjonForUnderenhet2.altinn3Tilganger.size shouldBe 2
            altinnOrganisasjonForUnderenhet2.erSlettet shouldBe false
            altinnOrganisasjonForUnderenhet2.underenheter.size shouldBe 0
        }
    }
}
