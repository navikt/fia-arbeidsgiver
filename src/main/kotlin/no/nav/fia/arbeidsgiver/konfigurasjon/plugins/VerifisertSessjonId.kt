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

fun VerifisertSesjonsId(spørreundersøkelseService: SpørreundersøkelseService) =
	createRouteScopedPlugin("VerifisertSesjonsId") {

		pluginConfig.apply {
			onCall { call ->
				spørreundersøkelseService.validerSesjonsId(
					sesjonsId = call.sesjonsId,
					spørreundersøkelseId = call.spørreundersøkelseId
				)
			}
		}
	}

private val ApplicationCall.sesjonsId
	get() = request.header(HEADER_SESJON_ID)?.tilUUID("sesjonsId") ?:
		throw Feil(feilmelding = "Mangler sesjonsId", feilkode = HttpStatusCode.Forbidden)

private fun SpørreundersøkelseService.validerSesjonsId(
	sesjonsId: UUID, spørreundersøkelseId: UUID
) {
	if (henteSpørreundersøkelseIdFraSesjon(sesjonsId) != spørreundersøkelseId)
		throw Feil(feilmelding = "Ugyldig sesjonsId", feilkode = HttpStatusCode.Forbidden)
}
