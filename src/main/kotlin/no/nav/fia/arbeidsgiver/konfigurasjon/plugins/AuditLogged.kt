package no.nav.fia.arbeidsgiver.konfigurasjon.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import no.nav.fia.arbeidsgiver.konfigurasjon.Cluster
import no.nav.fia.arbeidsgiver.konfigurasjon.Miljø
import no.nav.fia.arbeidsgiver.http.orgnr
import no.nav.fia.arbeidsgiver.http.tokenSubject
import org.slf4j.LoggerFactory
import java.util.UUID

val AuditLogged = createRouteScopedPlugin("AuditLogged") {
    val auditLog = LoggerFactory.getLogger("auditLogger")
    val fiaLog = LoggerFactory.getLogger("lokalLogger")

    pluginConfig.apply {
        onCallRespond { call ->
            val logstring =
                "CEF:0|fia-arbeidsgiver|auditLog|1.0|audit:access|fia-arbeidsgiver|INFO|end=${System.currentTimeMillis()} " +
                        "suid=${call.request.tokenSubject()} " +
                        "duid=${call.orgnr} " +
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

