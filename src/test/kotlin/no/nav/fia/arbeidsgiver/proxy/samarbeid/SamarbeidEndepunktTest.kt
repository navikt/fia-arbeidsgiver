package no.nav.fia.arbeidsgiver.proxy.samarbeid

import io.kotest.matchers.shouldBe
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.toKotlinLocalDateTime
import kotlinx.serialization.json.Json
import no.nav.fia.arbeidsgiver.helper.AltinnTilgangerContainerHelper.Companion.ALTINN_ORGNR_1
import no.nav.fia.arbeidsgiver.helper.TestContainerHelper
import no.nav.fia.arbeidsgiver.helper.TestContainerHelper.Companion.altinnTilgangerContainerHelper
import no.nav.fia.arbeidsgiver.helper.TestContainerHelper.Companion.lydiaApiContainerHelper
import no.nav.fia.arbeidsgiver.helper.withTokenXToken
import no.nav.fia.arbeidsgiver.helper.withoutGyldigTokenXToken
import no.nav.fia.arbeidsgiver.samarbeidsstatus.api.AltinnTilgangerService.Companion.ENKELRETTIGHET_FOREBYGGE_FRAVÆR_SAMARBEID
import java.time.LocalDateTime.now
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
            altinn3RettighetForUnderenhet = ENKELRETTIGHET_FOREBYGGE_FRAVÆR_SAMARBEID,
            erSlettet = true,
        )
        lydiaApiContainerHelper.leggTilSamarbeid(
            orgnr = "999888777",
            iaSamarbeidDto = SamarbeidService.IASamarbeidDto(
                id = 1234,
                navn = "Avdeling Oslo",
                status = SamarbeidService.IASamarbeidDto.Status.AKTIV,
                saksnummer = "S123456",
                opprettet = now().toKotlinLocalDateTime(),
            )
        )

        runBlocking {
            val response = TestContainerHelper.hentListeAvSamarbeidResponse(
                orgnr = ALTINN_ORGNR_1,
                config = withTokenXToken(
                    mapOf(
                        "acr" to "Level4",
                        "pid" to "123",
                    )
                ),
            )

            response.status.value shouldBe 200
            val result = Json.decodeFromString<List<SamarbeidService.IASamarbeidDto>>(response.bodyAsText())
            result shouldBe emptyList()
        }
    }

    @Test
    fun `Innlogget bruker får hente et samarbeid i den organisasjonen hen har tilgang til`() {
        altinnTilgangerContainerHelper.leggTilRettighet(
            orgnrTilUnderenhet = ALTINN_ORGNR_1,
            altinn3RettighetForUnderenhet = ENKELRETTIGHET_FOREBYGGE_FRAVÆR_SAMARBEID,
            erSlettet = true,
        )
        lydiaApiContainerHelper.leggTilSamarbeid(
            orgnr = ALTINN_ORGNR_1,
            iaSamarbeidDto = SamarbeidService.IASamarbeidDto(
                id = 1234,
                navn = "Avdeling Oslo",
                status = SamarbeidService.IASamarbeidDto.Status.AKTIV,
                saksnummer = "S123456",
                opprettet = now().toKotlinLocalDateTime(),
            )
        )

        runBlocking {
            val response = TestContainerHelper.hentListeAvSamarbeidResponse(
                orgnr = ALTINN_ORGNR_1,
                config = withTokenXToken(
                    mapOf(
                        "acr" to "Level4",
                        "pid" to "123",
                    )
                ),
            )

            response.status.value shouldBe 200
            val result = Json.decodeFromString<List<SamarbeidService.IASamarbeidDto>>(response.bodyAsText())
            result.size shouldBe 1
            result.first().status shouldBe SamarbeidService.IASamarbeidDto.Status.AKTIV
        }
    }
}
