package no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto

import kotlinx.serialization.Serializable
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.Spørreundersøkelse
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.SpørsmålOgSvaralternativer
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.temaFraSpørsmålId
import no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.dto.SvaralternativDto

@Serializable
data class SpørsmålsoversiktDto(
    val temabeskrivelse: String,
    val spørsmålTekst: String,
    val svaralternativer: List<SvaralternativDto>,
    val nesteSpørsmål: IdentifiserbartSpørsmål?,
    val forrigeSpørsmål: IdentifiserbartSpørsmål?,
    val antallTema: Int,
    val temanummer: Int,
    val antallSpørsmål: Int,
    val spørsmålnummer: Int,
)

fun SpørsmålOgSvaralternativer.tilSpørsmålsoversiktDto(spørreundersøkelse: Spørreundersøkelse): SpørsmålsoversiktDto {
    val tema = spørreundersøkelse.temaMedSpørsmålOgSvaralternativer.temaFraSpørsmålId(id)
    return SpørsmålsoversiktDto(
        temabeskrivelse = tema.beskrivelse,
        spørsmålTekst = spørsmål,
        svaralternativer = svaralternativer.map {
            SvaralternativDto(
                svarId = it.svarId.toString(),
                svartekst = it.svartekst
            )
        },
        nesteSpørsmål = spørreundersøkelse.hentNesteSpørsmålOgTema(id),
        forrigeSpørsmål = spørreundersøkelse.hentForrigeSpørsmålOgTema(id),
        temanummer = spørreundersøkelse.temaMedSpørsmålOgSvaralternativer.indexOfFirst { it.temaId == tema.temaId } + 1,
        antallTema = spørreundersøkelse.temaMedSpørsmålOgSvaralternativer.size,
        spørsmålnummer = tema.indeksFraSpørsmålId(spørsmålId = id) + 1,
        antallSpørsmål = tema.spørsmålOgSvaralternativer.size
    )
}

