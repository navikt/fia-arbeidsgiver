package no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto

import kotlinx.serialization.Serializable
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.Spørreundersøkelse
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.spørsmålFraId
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.temaFraSpørsmålId
import java.util.UUID

@Serializable
data class DeltakerSpørsmålDto(
    val spørsmål: SpørsmålDto,
    val spørsmålnummer: Int,
    val antallSpørsmål: Int,
    val temanummer: Int,
    val antallTema: Int,
    val temanavn: String,
    val nesteSpørsmål: IdentifiserbartSpørsmålDto?,
    val forrigeSpørsmål: IdentifiserbartSpørsmålDto?,
)

fun Spørreundersøkelse.tilDeltakerSpørsmål(spørsmålId: UUID): DeltakerSpørsmålDto {
    val tema = temaer.temaFraSpørsmålId(spørsmålId)
    return DeltakerSpørsmålDto(
        temanavn = tema.navn,
        nesteSpørsmål = hentNesteSpørsmålOgTema(spørsmålId),
        forrigeSpørsmål = hentForrigeSpørsmålOgTema(spørsmålId),
        temanummer = temaer.indexOfFirst { it.id == tema.id } + 1,
        antallTema = temaer.size,
        spørsmålnummer = tema.indeksFraSpørsmålId(spørsmålId = spørsmålId) + 1,
        antallSpørsmål = tema.spørsmål.size,
        spørsmål = temaer.spørsmålFraId(spørsmålId = spørsmålId).tilDto(),
    )
}
