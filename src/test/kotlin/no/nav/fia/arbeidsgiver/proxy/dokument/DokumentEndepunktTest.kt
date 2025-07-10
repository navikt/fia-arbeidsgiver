package no.nav.fia.arbeidsgiver.proxy.dokument

import io.kotest.matchers.shouldBe
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import no.nav.fia.arbeidsgiver.helper.AltinnTilgangerContainerHelper.Companion.ALTINN_ORGNR_1
import no.nav.fia.arbeidsgiver.helper.TestContainerHelper
import no.nav.fia.arbeidsgiver.helper.TestContainerHelper.Companion.altinnTilgangerContainerHelper
import no.nav.fia.arbeidsgiver.helper.TestContainerHelper.Companion.dokumentPubliseringContainerHelper
import no.nav.fia.arbeidsgiver.helper.withTokenXToken
import no.nav.fia.arbeidsgiver.helper.withoutGyldigTokenXToken
import no.nav.fia.arbeidsgiver.samarbeidsstatus.api.AltinnTilgangerService.Companion.ENKELRETTIGHET_FOREBYGGE_FRAVÆR_SAMARBEID
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test

class DokumentEndepunktTest {
    @BeforeTest
    fun setup() {
        runBlocking {
            dokumentPubliseringContainerHelper.slettAlleDokumenter()
        }
    }

    @Test
    fun `Uinnlogget bruker får en 401 - Not Authorized i response`() {
        runBlocking {
            val response = TestContainerHelper.hentDokumenterResponse(
                orgnr = ALTINN_ORGNR_1,
                config = withoutGyldigTokenXToken(),
            )

            response.status.value shouldBe 401
        }
    }

    @Test
    fun `Innlogget bruker som ikke har noen rettigheter får en 401 - Not Authorized i response`() {
        runBlocking {
            val response = TestContainerHelper.hentDokumenterResponse(
                orgnr = ALTINN_ORGNR_1,
                config = withoutGyldigTokenXToken(),
            )

            response.status.value shouldBe 401
        }
    }

    @Test
    fun `Innlogget bruker får hente organisasjoner hen har tilgang til`() {
        altinnTilgangerContainerHelper.leggTilRettighet(
            orgnrTilUnderenhet = ALTINN_ORGNR_1,
            altinn3RettighetForUnderenhet = ENKELRETTIGHET_FOREBYGGE_FRAVÆR_SAMARBEID,
            erSlettet = true,
        )

        dokumentPubliseringContainerHelper.leggTilDokument(
            orgnr = ALTINN_ORGNR_1,
            dokument = DokumentService.DokumentDto(
                dokumentId = UUID.randomUUID().toString(),
                type = "BEHOVSVURDERING",
                samarbeidNavn = "Avdeling Oslo",
                innhold = "{}",
            )
        )

        runBlocking {
            val response = TestContainerHelper.hentDokumenterResponse(
                orgnr = ALTINN_ORGNR_1,
                config = withTokenXToken(
                    mapOf(
                        "acr" to "Level4",
                        "pid" to "123",
                        "testclaim" to "testvalue",
                    )
                ),
            )

            response.status.value shouldBe 200
            val dokumenter = Json.decodeFromString<List<DokumentService.DokumentDto>>(response.bodyAsText())

            dokumenter.size shouldBe 1
        }
    }
}
