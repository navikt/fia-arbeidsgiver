package no.nav.api.kartlegging

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
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration
import kotlinx.coroutines.time.delay

class KartleggingApiTest {

    @Test
    fun `skal kunne hente ut kartlegging`() {
        val id = UUID.randomUUID()
        val pinKode = "123456"
        TestContainerHelper.kafka.sendKartlegging(id = id, pinKode = pinKode)

        runBlocking {
            val response = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = "$PATH/$id",
                body = pinKode
            )
            response.status shouldBe HttpStatusCode.OK
            val body = response.bodyAsText()
            val spørreundersøkelseDTO = Json.decodeFromString<SpørreundersøkelseDTO>(body)
            spørreundersøkelseDTO.id shouldBe id.toString()
            spørreundersøkelseDTO.sesjonsId shouldHaveLength UUID.randomUUID().toString().length
        }
    }

    @Test
    fun `skal begrense antall forespørsler mot kartlegging-bli-med`() {

        runBlocking {
            delay(3.seconds.toJavaDuration())
            repeat(5) {
                val response = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                    url = "$PATH/${UUID.randomUUID()}",
                    body = "tullogtøys"
                )
                response.status shouldBe HttpStatusCode.NotFound
            }
            val response = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = "$PATH/${UUID.randomUUID()}",
                body = "tullogtøys"
            )
            response.status shouldBe HttpStatusCode.TooManyRequests
            delay(3.seconds.toJavaDuration())
        }
    }

    @Test
    fun `returnerer BAD_REQUEST dersom UUID er feil formatert`() {

        runBlocking {
            val response = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = "$PATH/tullogtøys",
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
                url = "$PATH/$id",
                body = "654321"
            )
            response.status shouldBe HttpStatusCode.Unauthorized
            val body = response.bodyAsText()

            body shouldBe ""
        }
    }
}
