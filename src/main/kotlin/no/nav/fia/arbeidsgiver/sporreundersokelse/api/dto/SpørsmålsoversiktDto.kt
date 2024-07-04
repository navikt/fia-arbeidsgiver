package no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto

import java.util.UUID
import kotlinx.serialization.Serializable
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.Spørreundersøkelse
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.spørsmålFraId
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.temaFraSpørsmålId

@Serializable
data class SpørsmålsoversiktDto(
    val spørsmål: SpørsmålDto,
    val spørsmålnummer: Int,
    val antallSpørsmål: Int,
    val antallTema: Int,
    val temanummer: Int,
    val temabeskrivelse: String,
    val forrigeSpørsmål: IdentifiserbartSpørsmål?,
    val nesteSpørsmål: IdentifiserbartSpørsmål?,
)

fun Spørreundersøkelse.tilSpørsmålsoversiktDto(spørsmålId: UUID): SpørsmålsoversiktDto {
    val tema = temaer.temaFraSpørsmålId(spørsmålId)
    return SpørsmålsoversiktDto(
        temabeskrivelse = tema.beskrivelse ?: tema.navn!!,
        nesteSpørsmål = hentNesteSpørsmålOgTema(spørsmålId),
        forrigeSpørsmål = hentForrigeSpørsmålOgTema(spørsmålId),
        temanummer = temaer.indexOfFirst { it.id == tema.id } + 1,
        antallTema = temaer.size,
        spørsmålnummer = tema.indeksFraSpørsmålId(spørsmålId = spørsmålId) + 1,
        antallSpørsmål = tema.spørsmål.size,
        spørsmål = temaer.spørsmålFraId(spørsmålId = spørsmålId).tilDto()
    )
}

