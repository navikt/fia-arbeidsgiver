package no.nav.audit

import kotlinx.coroutines.runBlocking
import no.nav.api.samarbeidsstatus.STATUS_PATH
import no.nav.helper.AltinnProxyContainer
import no.nav.helper.TestContainerHelper
import no.nav.helper.TestContainerHelper.Companion.shouldContainLog
import no.nav.helper.performGet
import no.nav.helper.withToken
import kotlin.test.Test

class AuditLogTest {

    @Test
    fun `det skal auditlogges (Permit) dersom man går mot status med gyldig token og altinn tilgang`() {
        runBlocking {
            val orgnr = AltinnProxyContainer.ALTINN_ORGNR_1
            TestContainerHelper.fiaArbeidsgiverApi.performGet("$STATUS_PATH/$orgnr", withToken())
            TestContainerHelper.fiaArbeidsgiverApi shouldContainLog auditLog(
                fnr = "123",
                orgnummer = orgnr,
                tillat = "Permit"
            )
        }
    }

    @Test
    fun `det skal auditlogges (Deny) dersom man går mot status med gyldig token uten altinn tilgang`() {
        runBlocking {
            val orgnr = AltinnProxyContainer.ORGNR_UTEN_TILKNYTNING
            TestContainerHelper.fiaArbeidsgiverApi.performGet(
                "$STATUS_PATH/$orgnr", withToken()
            )
            TestContainerHelper.fiaArbeidsgiverApi shouldContainLog auditLog(
                fnr = "123",
                orgnummer = orgnr,
                tillat = "Deny"
            )
        }
    }

    private fun auditLog(
        fnr: String,
        orgnummer: String,
        tillat: String,
    ) =
        ("CEF:0|fia-arbeidsgiver|auditLog|1.0|audit:access|fia-arbeidsgiver|INFO|end=[0-9]+ " +
                "suid=$fnr " +
                "duid=$orgnummer "+
                "sproc=.{36} " +
                "requestMethod=GET " +
                "request=$STATUS_PATH/$orgnummer " +
                "flexString1Label=Decision " +
                "flexString1=$tillat"
                ).replace("|", "\\|").replace("?", "\\?").toRegex()

}