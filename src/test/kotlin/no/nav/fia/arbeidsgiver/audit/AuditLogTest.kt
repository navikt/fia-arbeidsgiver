package no.nav.fia.arbeidsgiver.audit

import kotlinx.coroutines.runBlocking
import no.nav.fia.arbeidsgiver.helper.AltinnTilgangerContainerHelper.Companion.ALTINN_ORGNR_1
import no.nav.fia.arbeidsgiver.helper.TestContainerHelper
import no.nav.fia.arbeidsgiver.helper.TestContainerHelper.Companion.altinnTilgangerContainerHelper
import no.nav.fia.arbeidsgiver.helper.TestContainerHelper.Companion.applikasjon
import no.nav.fia.arbeidsgiver.helper.TestContainerHelper.Companion.kafka
import no.nav.fia.arbeidsgiver.helper.TestContainerHelper.Companion.shouldContainLog
import no.nav.fia.arbeidsgiver.helper.stengTema
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.VERT_BASEPATH
import org.junit.Before
import java.util.UUID
import kotlin.test.Test

class AuditLogTest {
    @Before
    fun cleanUp() = runBlocking { altinnTilgangerContainerHelper.slettAlleRettigheter() }

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
