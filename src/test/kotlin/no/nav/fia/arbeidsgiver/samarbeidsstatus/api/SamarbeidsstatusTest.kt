package no.nav.fia.arbeidsgiver.samarbeidsstatus.api

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import no.nav.fia.arbeidsgiver.helper.AltinnTilgangerContainerHelper.Companion.ALTINN_ORGNR_1
import no.nav.fia.arbeidsgiver.helper.AltinnTilgangerContainerHelper.Companion.ALTINN_ORGNR_2
import no.nav.fia.arbeidsgiver.helper.AltinnTilgangerContainerHelper.Companion.ORGNR_UTEN_TILKNYTNING
import no.nav.fia.arbeidsgiver.helper.TestContainerHelper
import no.nav.fia.arbeidsgiver.helper.TestContainerHelper.Companion.altinnTilgangerContainerHelper
import no.nav.fia.arbeidsgiver.helper.TestContainerHelper.Companion.applikasjon
import no.nav.fia.arbeidsgiver.helper.TestContainerHelper.Companion.kafka
import no.nav.fia.arbeidsgiver.helper.TestContainerHelper.Companion.shouldContainLog
import no.nav.fia.arbeidsgiver.helper.performGet
import no.nav.fia.arbeidsgiver.helper.withTokenXToken
import no.nav.fia.arbeidsgiver.samarbeidsstatus.api.AltinnTilgangerService.Companion.ENKELRETTIGHET_FOREBYGGE_FRAVÆR_SAMARBEID
import no.nav.fia.arbeidsgiver.samarbeidsstatus.api.dto.SamarbeidsstatusDTO
import no.nav.fia.arbeidsgiver.samarbeidsstatus.api.dto.Samarbeidsstaus
import org.junit.Before
import kotlin.test.Test

class SamarbeidsstatusTest {
    @Before
    fun cleanUp() = runBlocking { altinnTilgangerContainerHelper.slettAlleRettigheter() }

    @Test
    fun `skal kunne nå isalive og isready`() {
        runBlocking {
            applikasjon.performGet("internal/isalive").status shouldBe HttpStatusCode.OK
            applikasjon.performGet("internal/isready").status shouldBe HttpStatusCode.OK
        }
    }

    @Test
    fun `skal få 401 (Unauthorized) dersom man går mot status uten innlogging`() {
        runBlocking {
            applikasjon.performGet("$SAMARBEIDSSTATUS_PATH/123456789").status shouldBe HttpStatusCode.Unauthorized
        }
    }

    @Test
    fun `skal få 401 (Unauthorized) dersom man går mot status med ugyldig token`() {
        runBlocking {
            applikasjon.performGet("$SAMARBEIDSSTATUS_PATH/123456789") {
                header(
                    HttpHeaders.Authorization,
                    "Bearer " + TestContainerHelper.tokenXAccessToken(
                        subject = "123",
                        audience = "NEI OG NEI",
                        claims = mapOf(
                            "pid" to "123",
                            "acr" to "Level4",
                        ),
                    ).serialize(),
                )
            }.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    @Test
    fun `skal få 401 (Unauthorized) dersom man går mot status og har for lav ACR level`() {
        runBlocking {
            applikasjon.performGet("$SAMARBEIDSSTATUS_PATH/$ALTINN_ORGNR_1") {
                header(
                    HttpHeaders.Authorization,
                    "Bearer " + TestContainerHelper.tokenXAccessToken(
                        subject = "123",
                        audience = "hei",
                        claims = mapOf(
                            "pid" to "123",
                            "acr" to "Level3",
                        ),
                    ).serialize(),
                )
            }.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    @Test
    fun `skal få 200 (OK) dersom man går mot status med gyldig token og underenhet er slettet`() {
        altinnTilgangerContainerHelper.leggTilRettigheter(
            underenhet = ALTINN_ORGNR_1,
            altinn3Rettighet = ENKELRETTIGHET_FOREBYGGE_FRAVÆR_SAMARBEID,
            erSlettet = true,
        )
        runBlocking {
            applikasjon.performGet(
                "$SAMARBEIDSSTATUS_PATH/$ALTINN_ORGNR_1",
                withTokenXToken(),
            ).status shouldBe HttpStatusCode.OK
        }
    }

    @Test
    fun `skal få 200 (OK) dersom man går mot status med gyldig token og altinn3 tilgang`() {
        altinnTilgangerContainerHelper.leggTilRettigheter(
            underenhet = ALTINN_ORGNR_1,
            altinn3Rettighet = ENKELRETTIGHET_FOREBYGGE_FRAVÆR_SAMARBEID,
        )
        runBlocking {
            applikasjon.performGet(
                "$SAMARBEIDSSTATUS_PATH/$ALTINN_ORGNR_1",
                withTokenXToken(),
            ).status shouldBe HttpStatusCode.OK
        }
    }

    @Test
    fun `skal få 200 (OK) dersom man går mot status med gyldig token, gammel acr og altinn tilgang`() {
        altinnTilgangerContainerHelper.leggTilRettigheter(
            underenhet = ALTINN_ORGNR_1,
            altinn3Rettighet = ENKELRETTIGHET_FOREBYGGE_FRAVÆR_SAMARBEID,
        )
        runBlocking {
            applikasjon.performGet("$SAMARBEIDSSTATUS_PATH/$ALTINN_ORGNR_1") {
                header(
                    HttpHeaders.Authorization,
                    "Bearer " + TestContainerHelper.tokenXAccessToken(
                        subject = "123",
                        audience = "tokenx:fia-arbeidsgiver",
                        claims = mapOf(
                            "pid" to "123",
                            "acr" to "Level4",
                        ),
                    ).serialize(),
                )
            }.status shouldBe HttpStatusCode.OK
        }
    }

    @Test
    fun `skal få 200 (OK) dersom man går mot status med gyldig token, ny acr og altinn tilgang`() {
        altinnTilgangerContainerHelper.leggTilRettigheter(
            underenhet = ALTINN_ORGNR_1,
            altinn3Rettighet = ENKELRETTIGHET_FOREBYGGE_FRAVÆR_SAMARBEID,
        )
        runBlocking {
            applikasjon.performGet("$SAMARBEIDSSTATUS_PATH/$ALTINN_ORGNR_1") {
                header(
                    HttpHeaders.Authorization,
                    "Bearer " + TestContainerHelper.tokenXAccessToken(
                        subject = "123",
                        audience = "tokenx:fia-arbeidsgiver",
                        claims = mapOf(
                            "pid" to "123",
                            "acr" to "idporten-loa-high",
                        ),
                    ).serialize(),
                )
            }.status shouldBe HttpStatusCode.OK
        }
    }

    @Test
    fun `skal få 403 (Forbidden) dersom man går mot status med gyldig token uten altinn tilknytning`() {
        runBlocking {
            val response = applikasjon.performGet(
                "$SAMARBEIDSSTATUS_PATH/$ORGNR_UTEN_TILKNYTNING",
                withTokenXToken(),
            )
            response.status shouldBe HttpStatusCode.Forbidden
            response.bodyAsText() shouldContain "Ikke tilgang til orgnummer".toRegex()
            applikasjon.shouldContainLog("Ikke tilgang til orgnummer".toRegex())
        }
    }

    @Test
    fun `skal få 403 (Forbidden) dersom man går mot status med gyldig token uten altinn enkelrettighet`() {
        altinnTilgangerContainerHelper.leggTilRettigheter(
            underenhet = ORGNR_UTEN_TILKNYTNING,
        )
        runBlocking {
            val response = applikasjon.performGet(
                "$SAMARBEIDSSTATUS_PATH/$ORGNR_UTEN_TILKNYTNING",
                withTokenXToken(),
            )

            response.status shouldBe HttpStatusCode.Forbidden
            response.bodyAsText() shouldContain "Ikke tilgang til orgnummer".toRegex()
        }
    }

    @Test
    fun `skal få ut samarbeidsstatus IKKE_I_SAMARBEID for virksomhet i status != VI_BISTÅR`() {
        altinnTilgangerContainerHelper.leggTilRettigheter(
            underenhet = ALTINN_ORGNR_1,
            altinn3Rettighet = ENKELRETTIGHET_FOREBYGGE_FRAVÆR_SAMARBEID,
        )
        runBlocking {
            kafka.sendStatusOppdateringForVirksomhet(ALTINN_ORGNR_1, "VURDERES")

            val responsSomTekst = applikasjon.performGet(
                url = "$SAMARBEIDSSTATUS_PATH/$ALTINN_ORGNR_1",
                config = withTokenXToken(),
            ).bodyAsText()

            Json.decodeFromString<SamarbeidsstatusDTO>(responsSomTekst) shouldBe
                SamarbeidsstatusDTO(ALTINN_ORGNR_1, Samarbeidsstaus.IKKE_I_SAMARBEID)
        }
    }

    @Test
    fun `skal få ut samarbeidsstatus I_SAMARBEID for virksomhet i status VI_BISTÅR`() {
        altinnTilgangerContainerHelper.leggTilRettigheter(
            underenhet = ALTINN_ORGNR_1,
            altinn3Rettighet = ENKELRETTIGHET_FOREBYGGE_FRAVÆR_SAMARBEID,
        )
        runBlocking {
            kafka.sendStatusOppdateringForVirksomhet(ALTINN_ORGNR_1, "VI_BISTÅR")

            val responsSomTekst = applikasjon.performGet(
                url = "$SAMARBEIDSSTATUS_PATH/$ALTINN_ORGNR_1",
                config = withTokenXToken(),
            ).bodyAsText()

            Json.decodeFromString<SamarbeidsstatusDTO>(responsSomTekst) shouldBe
                SamarbeidsstatusDTO(ALTINN_ORGNR_1, Samarbeidsstaus.I_SAMARBEID)
        }
    }

    @Test
    fun `skal få samarbeidsstatus IKKE_I_SAMARBEID dersom vi ikke har noen data for virksomhet`() {
        altinnTilgangerContainerHelper.leggTilRettigheter(
            underenhet = ALTINN_ORGNR_2,
            altinn3Rettighet = ENKELRETTIGHET_FOREBYGGE_FRAVÆR_SAMARBEID,
        )
        runBlocking {
            val orgnr = ALTINN_ORGNR_2
            val responsSomTekst = applikasjon.performGet(
                url = "$SAMARBEIDSSTATUS_PATH/$orgnr",
                config = withTokenXToken(),
            ).bodyAsText()

            Json.decodeFromString<SamarbeidsstatusDTO>(responsSomTekst) shouldBe
                SamarbeidsstatusDTO(orgnr, Samarbeidsstaus.IKKE_I_SAMARBEID)
        }
    }
}
