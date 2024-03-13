import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.request.receive
import no.nav.fia.arbeidsgiver.http.Feil
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.VertshandlingRequest
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.tilUUID
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.SpørreundersøkelseService


fun VerifisertVertId(spørreundersøkelseService: SpørreundersøkelseService) =
	createRouteScopedPlugin("VerifisertVertId") {

		pluginConfig.apply {
			onCall {
				val vertshandlingRequest = it.receive<VertshandlingRequest>()
				val spørreundersøkelseId = vertshandlingRequest.spørreundersøkelseId.tilUUID("spørreundersøkelseId")
				val vertId = vertshandlingRequest.vertId.tilUUID("vertId")
				if (spørreundersøkelseService.hentePågåendeSpørreundersøkelse(spørreundersøkelseId).vertId != vertId)
					throw Feil(
						feilmelding = "Ugyldig vertId: $vertId",
						feilkode = HttpStatusCode.Forbidden
					)
			}
		}
	}