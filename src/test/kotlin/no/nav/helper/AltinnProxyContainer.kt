package no.nav.helper

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.apache.http.protocol.HTTP.CONTENT_TYPE
import org.testcontainers.Testcontainers

const val ALTINN_ORGNR_1 = "311111111"
const val ALTINN_ORGNR_2 = "322222222"
const val ORGNR_UTEN_TILKNYTNING = "300000000"
const val ALTINN_OVERORDNET_ORGNR = "400000000"

class AltinnProxyContainer {
    private val wireMock = WireMockServer(WireMockConfiguration.options().dynamicPort()).also {
        it.stubFor(
            WireMock.get(WireMock.urlPathEqualTo("/altinn/v2/organisasjoner"))
                .willReturn(
                    WireMock.ok()
                        .withHeader(CONTENT_TYPE, "application/json")
                        .withBody(
                            """[
                                {
                                    "Name": "Spenstig Tiger",
                                     "Type": "Business",
                                     "OrganizationNumber": "$ALTINN_ORGNR_1",
                                     "ParentOrganizationNumber": "$ALTINN_OVERORDNET_ORGNR",
                                     "OrganizationForm": "BEDR",
                                     "Status": "Active"
                                }, 
                                {
                                    "Name": "FIKTIVIA",
                                     "Type": "Business",
                                     "OrganizationNumber": "$ALTINN_ORGNR_2",
                                     "ParentOrganizationNumber": "$ALTINN_OVERORDNET_ORGNR",
                                     "OrganizationForm": "BEDR",
                                     "Status": "Active"
                                }
                            ]""".trimMargin()
                        )
                )
        )

        if (!it.isRunning) {
            it.start()
        }

        println("Starter Wiremock på port ${it.port()}")
        Testcontainers.exposeHostPorts(it.port())
    }

    fun getEnv() = mapOf(
        "ALTINN_RETTIGHETER_PROXY_URL" to "http://host.testcontainers.internal:${wireMock.port()}/altinn",
        "ALTINN_RETTIGHETER_PROXY_CLIENT_ID" to "hei",
    )
}