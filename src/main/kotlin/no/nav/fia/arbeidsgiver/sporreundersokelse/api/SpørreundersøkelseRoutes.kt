package no.nav.fia.arbeidsgiver.sporreundersokelse.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.fia.arbeidsgiver.http.Feil
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.BliMedDto
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.BliMedRequest
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.SpørreundersøkelseService
import java.util.UUID

const val SPØRREUNDERSØKELSE_PATH = "/fia-arbeidsgiver/sporreundersokelse"
const val BLI_MED_PATH = "$SPØRREUNDERSØKELSE_PATH/bli-med"

fun Route.spørreundersøkelse(spørreundersøkelseService: SpørreundersøkelseService) {
    post(BLI_MED_PATH) {
        val bliMedRequest = call.receive(BliMedRequest::class)

        val spørreundersøkelseId = bliMedRequest.spørreundersøkelseId.tilUUID("spørreundersøkelseId")

        val spørreundersøkelse = spørreundersøkelseService.hentePågåendeSpørreundersøkelse(spørreundersøkelseId)

        val sesjonsId = UUID.randomUUID()
        spørreundersøkelseService.lagreSesjon(sesjonsId, spørreundersøkelse.id)

        val antallDeltakere = spørreundersøkelseService.hentAntallDeltakere(spørreundersøkelse.id)
        spørreundersøkelseService.lagreAntallDeltakere(spørreundersøkelse.id, (antallDeltakere + 1))

        call.respond(
            status = HttpStatusCode.OK,
            message = BliMedDto(
                spørreundersøkelseId = spørreundersøkelse.id.toString(),
                sesjonsId = sesjonsId.toString(),
            ),
        )
    }
}

internal fun String.tilUUID(hvaErJeg: String) =
    try {
        UUID.fromString(this)
    } catch (e: IllegalArgumentException) {
        throw Feil("Ugyldig formatert UUID $hvaErJeg: $this", e, HttpStatusCode.BadRequest)
    }

internal val ApplicationCall.spørreundersøkelseId
    get() = parameters["spørreundersøkelseId"]?.tilUUID("spørreundersøkelseId")
        ?: throw Feil(feilmelding = "Mangler spørreundersøkelseId", feilkode = HttpStatusCode.BadRequest)

internal val ApplicationCall.temaId
    get() = parameters["temaId"]?.toInt()
        ?: throw Feil(feilmelding = "Mangler temaId", feilkode = HttpStatusCode.BadRequest)

internal val ApplicationCall.spørsmålId
    get() = parameters["spørsmålId"]?.tilUUID("spørsmålId")
        ?: throw Feil(feilmelding = "Mangler spørsmålId", feilkode = HttpStatusCode.BadRequest)
