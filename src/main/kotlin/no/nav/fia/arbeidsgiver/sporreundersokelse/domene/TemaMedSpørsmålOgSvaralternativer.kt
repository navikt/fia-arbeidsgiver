package no.nav.fia.arbeidsgiver.sporreundersokelse.domene

import kotlinx.serialization.Serializable

@Serializable
data class TemaMedSpørsmålOgSvaralternativer(
    val tema: Tema,
    val beskrivelse: String,
    val introtekst: String,
    val spørsmålOgSvaralternativer: List<SpørsmålOgSvaralternativer>,
)