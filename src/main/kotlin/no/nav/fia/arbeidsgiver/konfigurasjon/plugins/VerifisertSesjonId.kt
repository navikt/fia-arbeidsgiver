import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.request.header
import no.nav.fia.arbeidsgiver.http.Feil
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.spørreundersøkelseId
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.tilUUID
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.SpørreundersøkelseService
import java.util.*

const val HEADER_SESJON_ID = "nav-fia-kartlegging-sesjon-id"

fun VerifisertSesjonId(spørreundersøkelseService: SpørreundersøkelseService) =
    createRouteScopedPlugin("VerifisertSesjonId") {

        pluginConfig.apply {
            onCall { call ->
                spørreundersøkelseService.validerSesjonId(
                    sesjonId = call.sesjonId,
                    spørreundersøkelseId = call.spørreundersøkelseId
                )
            }
        }
    }

val ApplicationCall.sesjonId
    get() = request.header(HEADER_SESJON_ID)?.tilUUID("sesjonId") ?: throw Feil(
        feilmelding = "Mangler sesjonId",
        feilkode = HttpStatusCode.Forbidden
    )

private fun SpørreundersøkelseService.validerSesjonId(
	sesjonId: UUID, spørreundersøkelseId: UUID,
) {
    if (henteSpørreundersøkelseIdFraSesjon(sesjonId) != spørreundersøkelseId)
        throw Feil(feilmelding = "Ugyldig sesjonId", feilkode = HttpStatusCode.Forbidden)
}
