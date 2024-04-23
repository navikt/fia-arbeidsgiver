package no.nav.fia.arbeidsgiver.konfigurasjon.plugins

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.request.httpMethod
import io.ktor.server.request.uri
import no.nav.fia.arbeidsgiver.http.fnrFraClaim
import no.nav.fia.arbeidsgiver.http.navIdentFraClaim
import no.nav.fia.arbeidsgiver.http.orgnr
import no.nav.fia.arbeidsgiver.konfigurasjon.Cluster
import no.nav.fia.arbeidsgiver.konfigurasjon.Miljø
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.SPØRREUNDERSØKELSE_PATH
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.spørreundersøkelseId
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.SpørreundersøkelseService
import org.slf4j.LoggerFactory
import java.util.*

fun AuditLogged(spørreundersøkelseService: SpørreundersøkelseService) = createRouteScopedPlugin("AuditLogged") {
    val auditLog = LoggerFactory.getLogger("auditLogger")
    val fiaLog = LoggerFactory.getLogger("lokalLogger")

    pluginConfig.apply {
        onCallRespond { call ->
            val logstring =
                "CEF:0|fia-arbeidsgiver|auditLog|1.0|audit:access|fia-arbeidsgiver|INFO|end=${System.currentTimeMillis()} " +
                        "suid=${call.hentSubjectId()} " +
                        "duid=${call.hentOrgnummer(spørreundersøkelseService)} " +
                        "sproc=${UUID.randomUUID()} " +
                        "requestMethod=${call.request.httpMethod.value} " +
                        "request=${
                            call.request.uri.substring(
                                0,
                                call.request.uri.length.coerceAtMost(70)
                            )
                        } " +
                        "flexString1Label=Decision " +
                        "flexString1=${if(call.response.status() == HttpStatusCode.OK) "Permit" else "Deny"}"


            when(Miljø.cluster) {
                Cluster.`prod-gcp`,
                Cluster.`dev-gcp` -> auditLog.info(logstring)
                Cluster.lokal -> fiaLog.info(logstring)
            }
        }
    }

}

private fun ApplicationCall.hentOrgnummer(spørreundersøkelseService: SpørreundersøkelseService) =
    if(erSpørreundersøkelseRoute()) {
        spørreundersøkelseService.hentePågåendeSpørreundersøkelse(spørreundersøkelseId).orgnummer
    }
    else
        this.orgnr

private fun ApplicationCall.hentSubjectId() =
    if (erSpørreundersøkelseRoute())
        request.navIdentFraClaim()
    else
        request.fnrFraClaim()

private fun ApplicationCall.erSpørreundersøkelseRoute() =
    request.uri.indexOf(SPØRREUNDERSØKELSE_PATH, ignoreCase = true) >= 0
