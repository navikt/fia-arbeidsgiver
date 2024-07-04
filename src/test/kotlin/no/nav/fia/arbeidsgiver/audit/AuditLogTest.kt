package no.nav.fia.arbeidsgiver.audit

import kotlinx.coroutines.runBlocking
import no.nav.fia.arbeidsgiver.helper.AltinnProxyContainer
import no.nav.fia.arbeidsgiver.helper.AltinnProxyContainer.Companion.ALTINN_ORGNR_1
import no.nav.fia.arbeidsgiver.helper.TestContainerHelper
import no.nav.fia.arbeidsgiver.helper.TestContainerHelper.Companion.fiaArbeidsgiverApi
import no.nav.fia.arbeidsgiver.helper.TestContainerHelper.Companion.kafka
import no.nav.fia.arbeidsgiver.helper.TestContainerHelper.Companion.shouldContainLog
import no.nav.fia.arbeidsgiver.helper.performGet
import no.nav.fia.arbeidsgiver.helper.stengTema
import no.nav.fia.arbeidsgiver.helper.withTokenXToken
import no.nav.fia.arbeidsgiver.samarbeidsstatus.api.SAMARBEIDSSTATUS_PATH
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.VERT_BASEPATH
import java.util.*
import kotlin.test.Test

class AuditLogTest {

    @Test
    fun `det skal auditlogges (Permit) dersom man går mot status med gyldig token og altinn tilgang`() {
        runBlocking {
            val orgnr = ALTINN_ORGNR_1
            fiaArbeidsgiverApi.performGet("$SAMARBEIDSSTATUS_PATH/$orgnr", withTokenXToken())
            fiaArbeidsgiverApi shouldContainLog auditLog(
                fnr = "123",
                orgnummer = orgnr,
                tillat = "Permit",
                uri = "$SAMARBEIDSSTATUS_PATH/$orgnr"
            )
        }
    }

    @Test
    fun `det skal auditlogges (Deny) dersom man går mot status med gyldig token uten altinn tilgang`() {
        runBlocking {
            val orgnr = AltinnProxyContainer.ORGNR_UTEN_TILKNYTNING
            fiaArbeidsgiverApi.performGet(
                "$SAMARBEIDSSTATUS_PATH/$orgnr", withTokenXToken()
            )
            fiaArbeidsgiverApi shouldContainLog auditLog(
                fnr = "123",
                orgnummer = orgnr,
                tillat = "Deny",
                uri = "$SAMARBEIDSSTATUS_PATH/$orgnr"
            )
        }
    }

    @Test
    fun `skal auditlogge stenging av tema og uthenting av resultater`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val spørreundersøkelse = kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId).tilDomene()
        val temaId = spørreundersøkelse.temaer.first().id
        runBlocking {
            fiaArbeidsgiverApi.stengTema(
                temaId = temaId,
                spørreundersøkelseId = spørreundersøkelse.id,
                vertId = spørreundersøkelse.vertId!!
            )
            fiaArbeidsgiverApi shouldContainLog auditLog(
                fnr = TestContainerHelper.VERT_NAV_IDENT,
                orgnummer = ALTINN_ORGNR_1,
                tillat = "Permit",
                uri = "$VERT_BASEPATH/${spørreundersøkelse.id}/tema/$temaId/avslutt",
                method = "POST",
            )
        }
    }

    private fun auditLog(
        fnr: String,
        orgnummer: String,
        tillat: String,
        uri: String,
        method: String = "GET",
    ) =
        ("CEF:0|fia-arbeidsgiver|auditLog|1.0|audit:access|fia-arbeidsgiver|INFO|end=[0-9]+ " +
                "suid=$fnr " +
                "duid=$orgnummer " +
                "sproc=.{36} " +
                "requestMethod=$method " +
                "request=${
                    uri.substring(
                        0,
                        uri.length.coerceAtMost(70)
                    )
                } " +
                "flexString1Label=Decision " +
                "flexString1=$tillat"
                ).replace("|", "\\|").replace("?", "\\?").toRegex()

}
