package no.nav.fia.arbeidsgiver.proxy.samarbeid

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.UUIDVersion
import io.kotest.matchers.string.shouldBeUUID
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import no.nav.fia.arbeidsgiver.helper.AltinnTilgangerContainerHelper.Companion.ALTINN_ORGNR_1
import no.nav.fia.arbeidsgiver.helper.LydiaApiContainerHelper
import no.nav.fia.arbeidsgiver.helper.TestContainerHelper
import no.nav.fia.arbeidsgiver.helper.TestContainerHelper.Companion.altinnTilgangerContainerHelper
import no.nav.fia.arbeidsgiver.helper.TestContainerHelper.Companion.lydiaApiContainerHelper
import no.nav.fia.arbeidsgiver.helper.withTokenXToken
import no.nav.fia.arbeidsgiver.helper.withoutGyldigTokenXToken
import no.nav.fia.arbeidsgiver.proxy.samarbeid.SamarbeidMedDokumenterDto.Companion.Status.AKTIV
import no.nav.fia.arbeidsgiver.samarbeidsstatus.api.AltinnTilgangerService.Companion.ENKELRETTIGHET_FOREBYGGE_FRAVÆR_IA_SAMARBEID
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test

class SamarbeidEndepunktTest {
    @BeforeTest
    fun setup() {
        runBlocking {
            lydiaApiContainerHelper.slettAlleSamarbeid()
        }
    }

    @Test
    fun `Uinnlogget bruker får en 401 - Not Authorized i response`() {
        runBlocking {
            val response = TestContainerHelper.hentListeAvSamarbeidResponse(
                orgnr = ALTINN_ORGNR_1,
                config = withoutGyldigTokenXToken(),
            )

            response.status.value shouldBe 401
        }
    }

    @Test
    fun `Innlogget bruker som ikke har noen rettigheter får en 401 - Not Authorized i response`() {
        runBlocking {
            val response = TestContainerHelper.hentListeAvSamarbeidResponse(
                orgnr = ALTINN_ORGNR_1,
                config = withoutGyldigTokenXToken(),
            )

            response.status.value shouldBe 401
        }
    }

    @Test
    fun `Innlogget bruker får 200 OK og en tom liste dersom ingen samarbeid finnes`() {
        altinnTilgangerContainerHelper.leggTilRettighet(
            orgnrTilUnderenhet = ALTINN_ORGNR_1,
            altinn3RettighetForUnderenhet = ENKELRETTIGHET_FOREBYGGE_FRAVÆR_IA_SAMARBEID,
            erSlettet = true,
        )
        lydiaApiContainerHelper.leggTilSamarbeid(
            orgnr = "999888777",
            iaSamarbeidDto = LydiaApiContainerHelper.SamarbeidMedDokumenterV1Dto(
                id = 1234,
                offentligId = UUID.randomUUID().toString(),
                navn = "Avdeling Oslo",
                status = AKTIV,
            ),
        )

        runBlocking {
            val response = TestContainerHelper.hentListeAvSamarbeidResponse(
                orgnr = ALTINN_ORGNR_1,
                config = withTokenXToken(
                    mapOf(
                        "acr" to "Level4",
                        "pid" to "123",
                    ),
                ),
            )

            response.status.value shouldBe 200
            val result = Json.decodeFromString<List<SamarbeidMedDokumenterDto>>(response.bodyAsText())
            result shouldBe emptyList()
        }
    }

    @Test
    fun `Innlogget bruker får hente et samarbeid i den organisasjonen hen har tilgang til`() {
        altinnTilgangerContainerHelper.leggTilRettighet(
            orgnrTilUnderenhet = ALTINN_ORGNR_1,
            altinn3RettighetForUnderenhet = ENKELRETTIGHET_FOREBYGGE_FRAVÆR_IA_SAMARBEID,
            erSlettet = true,
        )
        lydiaApiContainerHelper.leggTilSamarbeid(
            orgnr = ALTINN_ORGNR_1,
            iaSamarbeidDto = LydiaApiContainerHelper.SamarbeidMedDokumenterV1Dto(
                id = 1234,
                offentligId = UUID.randomUUID().toString(),
                navn = "Avdeling Oslo",
                status = AKTIV,
            ),
        )

        runBlocking {
            val response = TestContainerHelper.hentListeAvSamarbeidResponse(
                orgnr = ALTINN_ORGNR_1,
                config = withTokenXToken(
                    mapOf(
                        "acr" to "Level4",
                        "pid" to "123",
                    ),
                ),
            )

            response.status.value shouldBe 200
            val result = Json.decodeFromString<List<SamarbeidMedDokumenterDto>>(response.bodyAsText())
            result.size shouldBe 1
            result.first().status shouldBe AKTIV
            result.first().navn shouldBe "Avdeling Oslo"
            result.first().offentligId.shouldBeUUID(version = UUIDVersion.ANY)
        }
    }
}
