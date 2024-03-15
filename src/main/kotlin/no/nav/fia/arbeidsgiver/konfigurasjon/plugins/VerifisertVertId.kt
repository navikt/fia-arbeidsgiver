import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.request.header
import no.nav.fia.arbeidsgiver.http.Feil
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.spørreundersøkelseId
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.tilUUID
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.SpørreundersøkelseService

const val HEADER_VERT_ID = "nav-fia-kartlegging-vert-id"

fun VerifisertVertId(spørreundersøkelseService: SpørreundersøkelseService) =
	createRouteScopedPlugin("VerifisertVertId") {

		pluginConfig.apply {
			onCall { call ->
				val vertId = call.vertId
				val spørreundersøkelseId = call.spørreundersøkelseId
				if (spørreundersøkelseService.hentePågåendeSpørreundersøkelse(spørreundersøkelseId).vertId != vertId)
					throw Feil(
						feilmelding = "Ugyldig vertId: $vertId",
						feilkode = HttpStatusCode.Forbidden
					)
			}
		}
	}

private val ApplicationCall.vertId
	get() = request.header(HEADER_VERT_ID)?.tilUUID("vertId") ?:
	throw Feil(feilmelding = "Mangler vertId", feilkode = HttpStatusCode.Forbidden)