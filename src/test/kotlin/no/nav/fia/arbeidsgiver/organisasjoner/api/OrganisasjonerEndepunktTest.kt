package no.nav.fia.arbeidsgiver.organisasjoner.api

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import no.nav.fia.arbeidsgiver.helper.AltinnTilgangerContainerHelper.Companion.ALTINN_ORGNR_1
import no.nav.fia.arbeidsgiver.helper.AltinnTilgangerContainerHelper.Companion.ALTINN_OVERORDNET_ENHET
import no.nav.fia.arbeidsgiver.helper.TestContainerHelper
import no.nav.fia.arbeidsgiver.helper.TestContainerHelper.Companion.altinnTilgangerContainerHelper
import no.nav.fia.arbeidsgiver.helper.withTokenXToken
import no.nav.fia.arbeidsgiver.helper.withoutGyldigTokenXToken
import no.nav.fia.arbeidsgiver.samarbeidsstatus.api.AltinnTilgangerService.AltinnTilgang
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
            overordnetEnhet = ALTINN_OVERORDNET_ENHET,
            underenhet = ALTINN_ORGNR_1,
            altinn3Rettighet = "En annen enkeltrettighet",
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
            altinnOrganisasjonForOverordnetEnhet.underenheter.size shouldBe 1

            val altinnOrganisasjonForUnderenhet: AltinnTilgang =
                altinnOrganisasjonForOverordnetEnhet.underenheter.find { it.orgnr == ALTINN_ORGNR_1 }!!
            altinnOrganisasjonForUnderenhet.navn shouldBe "NAVN TIL UNDERENHET"
            altinnOrganisasjonForUnderenhet.altinn3Tilganger.size shouldBe 1
            altinnOrganisasjonForOverordnetEnhet.erSlettet shouldBe false
            altinnOrganisasjonForUnderenhet.underenheter.size shouldBe 0
        }
    }
}
