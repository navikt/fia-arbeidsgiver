package no.nav.fia.arbeidsgiver.audit

import kotlinx.coroutines.runBlocking
import no.nav.fia.arbeidsgiver.helper.AltinnTilgangerContainerHelper.Companion.ALTINN_ORGNR_1
import no.nav.fia.arbeidsgiver.helper.AltinnTilgangerContainerHelper.Companion.ORGNR_UTEN_TILKNYTNING
import no.nav.fia.arbeidsgiver.helper.TestContainerHelper
import no.nav.fia.arbeidsgiver.helper.TestContainerHelper.Companion.altinnTilgangerContainerHelper
import no.nav.fia.arbeidsgiver.helper.TestContainerHelper.Companion.applikasjon
import no.nav.fia.arbeidsgiver.helper.TestContainerHelper.Companion.kafka
import no.nav.fia.arbeidsgiver.helper.TestContainerHelper.Companion.shouldContainLog
import no.nav.fia.arbeidsgiver.helper.performGet
import no.nav.fia.arbeidsgiver.helper.stengTema
import no.nav.fia.arbeidsgiver.helper.withTokenXToken
import no.nav.fia.arbeidsgiver.samarbeidsstatus.api.AltinnTilgangerService.Companion.ENKELRETTIGHET_FOREBYGGE_FRAVÆR_I_ALTINN
import no.nav.fia.arbeidsgiver.samarbeidsstatus.api.SAMARBEIDSSTATUS_PATH
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.VERT_BASEPATH
import org.junit.Before
import java.util.UUID
import kotlin.test.Test

class AuditLogTest {
    @Before
    fun cleanUp() = runBlocking { altinnTilgangerContainerHelper.slettAlleRettigheter() }

    @Test
    fun `det skal auditlogges (Permit) dersom man går mot status med gyldig token og altinn tilgang`() {
        altinnTilgangerContainerHelper.leggTilRettigheter(
            underenhet = ALTINN_ORGNR_1,
            altinn2Rettighet = ENKELRETTIGHET_FOREBYGGE_FRAVÆR_I_ALTINN,
        )
        runBlocking {
            applikasjon.performGet("$SAMARBEIDSSTATUS_PATH/$ALTINN_ORGNR_1", withTokenXToken())
            applikasjon shouldContainLog auditLog(
                fnr = "123",
                orgnummer = ALTINN_ORGNR_1,
                tillat = "Permit",
                uri = "$SAMARBEIDSSTATUS_PATH/$ALTINN_ORGNR_1",
            )
        }
    }

    @Test
    fun `det skal auditlogges (Deny) dersom man går mot status med gyldig token uten altinn tilgang`() {
        runBlocking {
            applikasjon.performGet(
                "$SAMARBEIDSSTATUS_PATH/$ORGNR_UTEN_TILKNYTNING",
                withTokenXToken(),
            )
            applikasjon shouldContainLog auditLog(
                fnr = "123",
                orgnummer = ORGNR_UTEN_TILKNYTNING,
                tillat = "Deny",
                uri = "$SAMARBEIDSSTATUS_PATH/$ORGNR_UTEN_TILKNYTNING",
            )
        }
    }

    @Test
    fun `skal auditlogge stenging av tema og uthenting av resultater`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val spørreundersøkelse = kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId).tilDomene()
        val temaId = spørreundersøkelse.temaer.first().id
        runBlocking {
            applikasjon.stengTema(
                temaId = temaId,
                spørreundersøkelseId = spørreundersøkelse.id,
            )
            applikasjon shouldContainLog auditLog(
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
    ) = (
        "CEF:0|fia-arbeidsgiver|auditLog|1.0|audit:access|fia-arbeidsgiver|INFO|end=[0-9]+ " +
            "suid=$fnr " +
            "duid=$orgnummer " +
            "sproc=.{36} " +
            "requestMethod=$method " +
            "request=${
                uri.substring(
                    0,
                    uri.length.coerceAtMost(70),
                )
            } " +
            "flexString1Label=Decision " +
            "flexString1=$tillat"
    ).replace("|", "\\|").replace("?", "\\?").toRegex()
}
