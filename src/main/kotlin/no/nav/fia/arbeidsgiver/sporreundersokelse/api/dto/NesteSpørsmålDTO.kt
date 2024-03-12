package no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class NesteSpørsmålDTO(
    val erNesteÅpnetAvVert: Boolean,
    val nesteSpørsmålId: String?,
    val forrigeSpørsmålId: String?,
)