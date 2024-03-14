package no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class NesteSpørsmålDTO(
    val hvaErNesteSteg: String,
    val erNesteÅpnetAvVert: Boolean,
    val nesteSpørsmålId: String?,
    val forrigeSpørsmålId: String?,
)