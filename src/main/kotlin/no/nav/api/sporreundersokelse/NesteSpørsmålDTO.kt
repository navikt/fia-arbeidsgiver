package no.nav.api.sporreundersokelse

import kotlinx.serialization.Serializable

@Serializable
data class NesteSpørsmålDTO(
    val erSisteSpørsmål: Boolean,
    val erÅpnetAvVert: Boolean,
    val spørsmålId: String,
)
