package no.nav.api.kartlegging

import io.kotest.matchers.shouldBe
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
            Json.decodeFromString<SpørreundersøkelseDTO>(body).id shouldBe id.toString()
            //TODO: sjekk resten av feltene
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
        }
    }

    //TODO: skriv test som takler ugyldig UUID
    //TODO: skriv test som takler feil pin
}
