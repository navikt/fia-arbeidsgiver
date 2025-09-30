package no.nav.fia.arbeidsgiver.proxy.dokument

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import no.nav.fia.arbeidsgiver.helper.AltinnTilgangerContainerHelper.Companion.ALTINN_ORGNR_1
import no.nav.fia.arbeidsgiver.helper.TestContainerHelper
import no.nav.fia.arbeidsgiver.helper.TestContainerHelper.Companion.altinnTilgangerContainerHelper
import no.nav.fia.arbeidsgiver.helper.TestContainerHelper.Companion.dokumentPubliseringContainerHelper
import no.nav.fia.arbeidsgiver.helper.withTokenXToken
import no.nav.fia.arbeidsgiver.helper.withoutGyldigTokenXToken
import no.nav.fia.arbeidsgiver.samarbeidsstatus.api.AltinnTilgangerService.Companion.ENKELRETTIGHET_FOREBYGGE_FRAVÆR_IA_SAMARBEID
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
            val response = TestContainerHelper.hentDokumentResponse(
                orgnr = ALTINN_ORGNR_1,
                dokumentId = UUID.randomUUID(),
                config = withoutGyldigTokenXToken(),
            )

            response.status.value shouldBe 401
        }
    }

    @Test
    fun `Innlogget bruker som ikke har noen rettigheter får en 401 - Not Authorized i response`() {
        runBlocking {
            val response = TestContainerHelper.hentDokumentResponse(
                orgnr = ALTINN_ORGNR_1,
                dokumentId = UUID.randomUUID(),
                config = withoutGyldigTokenXToken(),
            )

            response.status.value shouldBe 401
        }
    }

    @Test
    fun `Innlogget bruker får 404 Not Found dersom dokument ikke finnes`() {
        val dokument = DokumentService.DokumentDto(
            dokumentId = UUID.randomUUID().toString(),
            type = "BEHOVSVURDERING",
            samarbeidNavn = "Avdeling Oslo",
            innhold = Json.decodeFromString("{}"),
        )
        altinnTilgangerContainerHelper.leggTilRettighet(
            orgnrTilUnderenhet = ALTINN_ORGNR_1,
            altinn3RettighetForUnderenhet = ENKELRETTIGHET_FOREBYGGE_FRAVÆR_IA_SAMARBEID,
            erSlettet = true,
        )
        dokumentPubliseringContainerHelper.leggTilDokument(
            orgnr = ALTINN_ORGNR_1,
            dokument = dokument,
        )

        runBlocking {
            val ukjentDokumentId = UUID.randomUUID()
            val response = TestContainerHelper.hentDokumentResponse(
                orgnr = ALTINN_ORGNR_1,
                dokumentId = ukjentDokumentId,
                config = withTokenXToken(
                    mapOf(
                        "acr" to "Level4",
                        "pid" to "123",
                    ),
                ),
            )

            response.status.value shouldBe 404
            val body = response.bodyAsText()

            body shouldBe "Ingen dokument med id '$ukjentDokumentId'"
        }
    }

    @Test
    fun `Innlogget bruker får hente et dokument hen har tilgang til`() {
        val dokument = DokumentService.DokumentDto(
            dokumentId = UUID.randomUUID().toString(),
            type = "BEHOVSVURDERING",
            samarbeidNavn = "Avdeling Oslo",
            innhold = Json.decodeFromString("{}"),
        )
        altinnTilgangerContainerHelper.leggTilRettighet(
            orgnrTilUnderenhet = ALTINN_ORGNR_1,
            altinn3RettighetForUnderenhet = ENKELRETTIGHET_FOREBYGGE_FRAVÆR_IA_SAMARBEID,
            erSlettet = true,
        )
        dokumentPubliseringContainerHelper.leggTilDokument(
            orgnr = ALTINN_ORGNR_1,
            dokument = dokument,
        )

        runBlocking {
            val response = TestContainerHelper.hentDokumentResponse(
                orgnr = ALTINN_ORGNR_1,
                dokumentId = UUID.fromString(dokument.dokumentId),
                config = withTokenXToken(
                    mapOf(
                        "acr" to "Level4",
                        "pid" to "123",
                    ),
                ),
            )

            response.status.value shouldBe 200
            val hentetDokument = Json.decodeFromString<DokumentService.DokumentDto>(response.bodyAsText())

            hentetDokument shouldNotBe null
            hentetDokument.dokumentId shouldBe dokument.dokumentId
        }
    }
}
