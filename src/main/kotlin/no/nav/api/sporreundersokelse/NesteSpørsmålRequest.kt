package no.nav.api.sporreundersokelse

import kotlinx.serialization.Serializable

@Serializable
data class NesteSpørsmålRequest(
    val spørreundersøkelseId: String,
    val sesjonsId: String,
    val nåværrendeSpørsmålId: String,
)