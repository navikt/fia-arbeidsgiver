package no.nav.helper

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.apache.http.protocol.HTTP.CONTENT_TYPE
import org.testcontainers.Testcontainers

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
                                    "Name": "BALLSTAD OG HAMARØY",
                                     "Type": "Business",
                                     "OrganizationNumber": "811076732",
                                     "ParentOrganizationNumber": "811076112",
                                     "OrganizationForm": "BEDR",
                                     "Status": "Active"
                                }, 
                                {
                                    "Name": "FIKTIVIA",
                                     "Type": "Business",
                                     "OrganizationNumber": "315829062",
                                     "ParentOrganizationNumber": "811076112",
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