package no.nav.api.kartlegging

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldHaveLength
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import no.nav.helper.TestContainerHelper
import no.nav.helper.performPost
import java.util.*
import kotlin.test.Test
import kotlin.time.toJavaDuration
import kotlinx.coroutines.time.delay
import no.nav.konfigurasjon.RateLimitKonfig

class KartleggingApiTest {

    @Test
    fun `skal kunne starte kartlegging`() {
        val id = UUID.randomUUID()
        val pinkode = "123456"
        TestContainerHelper.kafka.sendKartlegging(id = id, pinkode = pinkode)

        runBlocking {
            val response = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = BLI_MED_PATH,
                body = BliMedRequest(spørreundersøkelseId = id.toString(), pinkode =  pinkode)
            )
            response.status shouldBe HttpStatusCode.OK
            val body = response.bodyAsText()
            val bliMedDTO = Json.decodeFromString<BliMedDTO>(body)
            bliMedDTO.id shouldBe id.toString()
            bliMedDTO.sesjonsId shouldHaveLength UUID.randomUUID().toString().length
        }
    }

    @Test
    fun `skal begrense antall forespørsler mot kartlegging-bli-med`() {

        runBlocking {
            delay(RateLimitKonfig.refillPeriod.toJavaDuration())
            repeat(RateLimitKonfig.bliMedLimit) {
                val response = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                    url = BLI_MED_PATH,
                    body = BliMedRequest(spørreundersøkelseId = UUID.randomUUID().toString(), pinkode =  "tullogtøys")
                )
                response.status shouldBe HttpStatusCode.NotFound
            }
            val response = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = BLI_MED_PATH,
                body = BliMedRequest(spørreundersøkelseId = UUID.randomUUID().toString(), pinkode =  "tullogtøys")
            )
            response.status shouldBe HttpStatusCode.TooManyRequests
            delay(RateLimitKonfig.refillPeriod.toJavaDuration())
        }
    }

    @Test
    fun `returnerer BAD_REQUEST dersom UUID er feil formatert`() {

        runBlocking {
            val response = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = BLI_MED_PATH,
                body = BliMedRequest(spørreundersøkelseId = "tullogtøys", pinkode =  "654321")
            )
            response.status shouldBe HttpStatusCode.BadRequest
            val body = response.bodyAsText()

            body shouldBe ""
        }
    }

    @Test
    fun `returnerer FORBIDDEN dersom pin er feil`() {
        val id = UUID.randomUUID()
        val pinkode = "123456"
        TestContainerHelper.kafka.sendKartlegging(id = id, pinkode = pinkode)

        runBlocking {
            val response = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = BLI_MED_PATH,
                body = BliMedRequest(spørreundersøkelseId = id.toString(), pinkode = "654321")
            )
            response.status shouldBe HttpStatusCode.Forbidden
            val body = response.bodyAsText()

            body shouldBe ""
        }
    }

    @Test
    fun `skal kunne hente spørsmål og svar`() {
        val id = UUID.randomUUID()
        val pinkode = "123456"
        TestContainerHelper.kafka.sendKartlegging(id = id, pinkode = pinkode)

        runBlocking {
            val bliMedRespons = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = BLI_MED_PATH,
                body = BliMedRequest(spørreundersøkelseId = id.toString(), pinkode =  pinkode)
            )
            bliMedRespons.status shouldBe HttpStatusCode.OK
            val bliMedBody = bliMedRespons.bodyAsText()
            val bliMedDTO = Json.decodeFromString<BliMedDTO>(bliMedBody)

            val spørsmålOgSvarRespons = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = SPØRSMÅL_OG_SVAR_PATH,
                body = SpørsmålOgSvarRequest(spørreundersøkelseId = id.toString(), sesjonsId = bliMedDTO.sesjonsId)
            )
            spørsmålOgSvarRespons.status shouldBe HttpStatusCode.OK
            val body = spørsmålOgSvarRespons.bodyAsText()
            val spørsmålOgSvaralternativer = Json.decodeFromString<List<SpørsmålOgSvaralternativerDTO>>(body)

            spørsmålOgSvaralternativer shouldHaveSize 1
            spørsmålOgSvaralternativer.first().svaralternativer shouldHaveSize 2
        }
    }

    @Test
    fun `skal ikke få spørsmål og svar dersom sesjonsId er ukjent`() {
        val id = UUID.randomUUID()
        val pinkode = "123456"
        val sesjonsId = UUID.randomUUID()
        TestContainerHelper.kafka.sendKartlegging(id = id, pinkode = pinkode)

        runBlocking {
            val bliMedRespons = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = BLI_MED_PATH,
                body = BliMedRequest(spørreundersøkelseId = id.toString(), pinkode =  pinkode)
            )
            bliMedRespons.status shouldBe HttpStatusCode.OK

            val spørsmålOgSvarRespons = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = SPØRSMÅL_OG_SVAR_PATH,
                body = SpørsmålOgSvarRequest(spørreundersøkelseId = id.toString(), sesjonsId = sesjonsId.toString())
            )
            spørsmålOgSvarRespons.status shouldBe HttpStatusCode.Forbidden
        }
    }

    @Test
    fun `skal kunne sende inn et gyldig svar`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val pinkode = "123456"
        TestContainerHelper.kafka.sendKartlegging(id = spørreundersøkelseId, pinkode = pinkode)

        runBlocking {
            val bliMedRespons = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = BLI_MED_PATH,
                body = BliMedRequest(spørreundersøkelseId = spørreundersøkelseId.toString(), pinkode =  pinkode)
            )
            bliMedRespons.status shouldBe HttpStatusCode.OK
            val bliMedBody = bliMedRespons.bodyAsText()
            val bliMedDTO = Json.decodeFromString<BliMedDTO>(bliMedBody)

            val spørsmålOgSvarRespons = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = SPØRSMÅL_OG_SVAR_PATH,
                body = SpørsmålOgSvarRequest(spørreundersøkelseId = spørreundersøkelseId.toString(), sesjonsId = bliMedDTO.sesjonsId)
            )
            spørsmålOgSvarRespons.status shouldBe HttpStatusCode.OK
            val body = spørsmålOgSvarRespons.bodyAsText()
            val spørsmålOgSvaralternativer = Json.decodeFromString<List<SpørsmålOgSvaralternativerDTO>>(body)

            val spørsmål = spørsmålOgSvaralternativer.first()
            val svaralternativ = spørsmål.svaralternativer.first()

            val svarRespons = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = SVAR_PATH,
                body = SvarRequest(spørreundersøkelseId = spørreundersøkelseId.toString(),
                    spørsmålId = spørsmål.id.toString(), svaralternativ.id.toString())
            )
            svarRespons.status shouldBe HttpStatusCode.OK
        }

    }

    @Test
    fun `skal få feilkode ved ukjent spørreundersøkelse`(){

        runBlocking {
            val svarRespons = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = SVAR_PATH,
                body = SvarRequest(spørreundersøkelseId = UUID.randomUUID().toString(),
                    spørsmålId = UUID.randomUUID().toString(), UUID.randomUUID().toString())
            )
            svarRespons.status shouldBe HttpStatusCode.Forbidden
        }

    }

    @Test
    fun `skal få feilkode ved ukjent svar og svaralternativ`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val pinkode = "123456"
        TestContainerHelper.kafka.sendKartlegging(id = spørreundersøkelseId, pinkode = pinkode)

        runBlocking {
            val bliMedRespons = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = BLI_MED_PATH,
                body = BliMedRequest(spørreundersøkelseId = spørreundersøkelseId.toString(), pinkode =  pinkode)
            )
            bliMedRespons.status shouldBe HttpStatusCode.OK
            val bliMedBody = bliMedRespons.bodyAsText()
            val bliMedDTO = Json.decodeFromString<BliMedDTO>(bliMedBody)

            val spørsmålOgSvarRespons = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = SPØRSMÅL_OG_SVAR_PATH,
                body = SpørsmålOgSvarRequest(spørreundersøkelseId = spørreundersøkelseId.toString(), sesjonsId = bliMedDTO.sesjonsId)
            )
            spørsmålOgSvarRespons.status shouldBe HttpStatusCode.OK
            val body = spørsmålOgSvarRespons.bodyAsText()
            val spørsmålOgSvaralternativer = Json.decodeFromString<List<SpørsmålOgSvaralternativerDTO>>(body)

            val spørsmål = spørsmålOgSvaralternativer.first()
            val svaralternativ = spørsmål.svaralternativer.first()

            val svarRespons1 = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = SVAR_PATH,
                body = SvarRequest(spørreundersøkelseId = spørreundersøkelseId.toString(),
                    spørsmålId = UUID.randomUUID().toString(), svaralternativ.id.toString())
            )
            svarRespons1.status shouldBe HttpStatusCode.Forbidden

            val svarRespons2 = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = SVAR_PATH,
                body = SvarRequest(spørreundersøkelseId = spørreundersøkelseId.toString(),
                    spørsmålId = spørsmål.id.toString(), UUID.randomUUID().toString())
            )
            svarRespons2.status shouldBe HttpStatusCode.Forbidden
        }
    }

}
