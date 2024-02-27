package no.nav.fia.arbeidsgiver.api.sporreundersokelse

import kotlinx.serialization.Serializable

@Serializable
data class NesteSpørsmålRequest(
    val spørreundersøkelseId: String,
    val sesjonsId: String,
    val nåværrendeSpørsmålId: String,
)