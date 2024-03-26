package no.nav.fia.arbeidsgiver.sporreundersokelse.api.vert.dto

import kotlinx.serialization.Serializable
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.Temanavn
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.TemaMedSpørsmålOgSvaralternativer

@Serializable
data class TemaOversiktDto(
    val temaId: Int,
    val temanavn: Temanavn,
    val tittel: String,
    val beskrivelse: String,
    val introtekst: String,
    val førsteSpørsmålId: String,
)


fun TemaMedSpørsmålOgSvaralternativer.tilTemaOversiktDto() = TemaOversiktDto(
    temaId = temaId,
    temanavn = temanavn,
    tittel = temanavn.name,
    beskrivelse = beskrivelse,
    introtekst = introtekst,
    førsteSpørsmålId = spørsmålOgSvaralternativer.first().id.toString()
)

fun List<TemaMedSpørsmålOgSvaralternativer>.tilTemaOversiktDto() = map { it.tilTemaOversiktDto() }