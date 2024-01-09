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
import no.nav.domene.kartlegging.SpørsmålOgSvaralternativer
import no.nav.konfigurasjon.RateLimitKonfig

class KartleggingApiTest {

    @Test
    fun `skal kunne starte kartlegging`() {
        val id = UUID.randomUUID()
        val pinKode = "123456"
        TestContainerHelper.kafka.sendKartlegging(id = id, pinKode = pinKode)

        runBlocking {
            val response = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = "$BLI_MED_PATH/$id",
                body = pinKode
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
                    url = "$BLI_MED_PATH/${UUID.randomUUID()}",
                    body = "tullogtøys"
                )
                response.status shouldBe HttpStatusCode.NotFound
            }
            val response = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = "$BLI_MED_PATH/${UUID.randomUUID()}",
                body = "tullogtøys"
            )
            response.status shouldBe HttpStatusCode.TooManyRequests
            delay(RateLimitKonfig.refillPeriod.toJavaDuration())
        }
    }

    @Test
    fun `returnerer BAD_REQUEST dersom UUID er feil formatert`() {

        runBlocking {
            val response = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = "$BLI_MED_PATH/tullogtøys",
                body = "654321"
            )
            response.status shouldBe HttpStatusCode.BadRequest
            val body = response.bodyAsText()

            body shouldBe ""
        }
    }

    @Test
    fun `returnerer UNAUTHORIZED dersom pin er feil`() {
        val id = UUID.randomUUID()
        val pinKode = "123456"
        TestContainerHelper.kafka.sendKartlegging(id = id, pinKode = pinKode)

        runBlocking {
            val response = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = "$BLI_MED_PATH/$id",
                body = "654321"
            )
            response.status shouldBe HttpStatusCode.Unauthorized
            val body = response.bodyAsText()

            body shouldBe ""
        }
    }

    @Test
    fun `skal kunne hente spørsmål og svar`() {
        val id = UUID.randomUUID()
        val pinKode = "123456"
        TestContainerHelper.kafka.sendKartlegging(id = id, pinKode = pinKode)

        runBlocking {
            val bliMedRespons = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = "$BLI_MED_PATH/$id",
                body = pinKode
            )
            bliMedRespons.status shouldBe HttpStatusCode.OK
            val bliMedBody = bliMedRespons.bodyAsText()
            val bliMedDTO = Json.decodeFromString<BliMedDTO>(bliMedBody)

            val spørsmålOgSvarRespons = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = "$SPØRSMÅL_OG_SVAR_PATH/$id",
                body = bliMedDTO.sesjonsId
            )
            spørsmålOgSvarRespons.status shouldBe HttpStatusCode.OK
            val body = spørsmålOgSvarRespons.bodyAsText()
            val spørsmålOgSvaralternativer = Json.decodeFromString<List<SpørsmålOgSvaralternativer>>(body)

            spørsmålOgSvaralternativer shouldHaveSize 1
            spørsmålOgSvaralternativer.first().svaralternativer shouldHaveSize 2
        }
    }

    @Test
    fun `skal ikke få spørsmål og svar dersom sesjonsId er ukjent`() {
        val id = UUID.randomUUID()
        val pinKode = "123456"
        val sesjonsId = UUID.randomUUID()
        TestContainerHelper.kafka.sendKartlegging(id = id, pinKode = pinKode)

        runBlocking {
            val bliMedRespons = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = "$BLI_MED_PATH/$id",
                body = pinKode
            )
            bliMedRespons.status shouldBe HttpStatusCode.OK

            val spørsmålOgSvarRespons = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = "$SPØRSMÅL_OG_SVAR_PATH/$id",
                body = sesjonsId.toString()
            )
            spørsmålOgSvarRespons.status shouldBe HttpStatusCode.Unauthorized
        }
    }

}
