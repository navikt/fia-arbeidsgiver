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
        }
    }
}
