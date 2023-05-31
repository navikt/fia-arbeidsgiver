package no.nav.api

import io.kotest.matchers.shouldBe
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import no.nav.helper.*
import no.nav.helper.AltinnProxyContainer.Companion.ALTINN_ORGNR_1
import no.nav.helper.AltinnProxyContainer.Companion.ORGNR_UTEN_TILKNYTNING
import no.nav.helper.TestContainerHelper.Companion.fiaArbeidsgiverApi
import no.nav.helper.performGet
import no.nav.helper.withToken
import kotlin.test.Test

class ArbeidsgiverStatusTest {
    @Test
    fun `skal kunne nå isalive og isready`() {
        runBlocking {
            fiaArbeidsgiverApi.performGet("internal/isalive").status shouldBe HttpStatusCode.OK
            fiaArbeidsgiverApi.performGet("internal/isready").status shouldBe HttpStatusCode.OK
        }
    }

    @Test
    fun `skal få 401 (Unauthorized) dersom man går mot status uten innlogging`() {
        runBlocking {
            fiaArbeidsgiverApi.performGet("status/123456789").status shouldBe HttpStatusCode.Unauthorized
        }
    }

    @Test
    fun `skal få 401 (Unauthorized) dersom man går mot status med ugyldig token`() {
        runBlocking {
            fiaArbeidsgiverApi.performGet("status/123456789") {
                header(
                    HttpHeaders.Authorization, "Bearer " + TestContainerHelper.accessToken(
                        subject = "123",
                        audience = "NEI OG NEI",
                        claims = mapOf(
                            "pid" to "123",
                            "acr" to "Level4"
                        ),
                    ).serialize()
                )
            }.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    @Test
    fun `skal få 401 (Unauthorized) dersom man går mot status og har for lav ACR level`() {
        runBlocking {
            fiaArbeidsgiverApi.performGet("status/$ALTINN_ORGNR_1") {
                header(
                    HttpHeaders.Authorization, "Bearer " + TestContainerHelper.accessToken(
                        subject = "123",
                        audience = "hei",
                        claims = mapOf(
                            "pid" to "123",
                            "acr" to "Level3"
                        ),
                    ).serialize()
                )
            }.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    @Test
    fun `skal få 200 (OK) dersom man går mot status med gyldig token og altinn tilgang`() {
        runBlocking {
            fiaArbeidsgiverApi.performGet(
                "status/$ALTINN_ORGNR_1", withToken()
            ).status shouldBe HttpStatusCode.OK
        }
    }

    @Test
    fun `skal få 403 (Forbidden) dersom man går mot status med gyldig token uten altinn tilgang`() {
        runBlocking {
            fiaArbeidsgiverApi.performGet(
                "status/$ORGNR_UTEN_TILKNYTNING", withToken()
            ).status shouldBe HttpStatusCode.Forbidden
        }
    }
}
