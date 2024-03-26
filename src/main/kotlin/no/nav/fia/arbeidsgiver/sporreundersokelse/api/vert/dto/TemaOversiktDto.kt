package no.nav.fia.arbeidsgiver.sporreundersokelse.api.vert.dto

import kotlinx.serialization.Serializable
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.Temanavn
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.TemaMedSpørsmålOgSvaralternativer

@Serializable
data class TemaOversiktDto(
    val temaId: Int,
    val temanavn: Temanavn,
    val del: Int, // starter fra 1 (er ikke en list-index)
    val tittel: String,
    val beskrivelse: String,
    val introtekst: String,
    val førsteSpørsmålId: String,
)



fun TemaMedSpørsmålOgSvaralternativer.tilTemaOversiktDto(del: Int) = TemaOversiktDto(
    temaId = temaId,
    temanavn = temanavn,
    del = del,
    tittel = temanavn.name,
    beskrivelse = beskrivelse,
    introtekst = introtekst,
    førsteSpørsmålId = spørsmålOgSvaralternativer.first().id.toString()
)

fun List<TemaMedSpørsmålOgSvaralternativer>.tilTemaOversiktDto(): List<TemaOversiktDto> =
    mapIndexed { index, temaMedSpørsmålOgSvaralternativer ->
        temaMedSpørsmålOgSvaralternativer.tilTemaOversiktDto(del = index + 1)
    }