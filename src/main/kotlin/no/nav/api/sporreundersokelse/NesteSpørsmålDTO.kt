package no.nav.api.sporreundersokelse

import kotlinx.serialization.Serializable

@Serializable
data class NesteSpørsmålDTO(
    val nåværendeSpørsmålIndeks: Int,
    val sisteSpørsmålIndeks: Int,
    val hvaErNesteSteg: StegStatus,
    val erNesteÅpnetAvVert: Boolean,
    val nesteSpørsmålId: String?,
    val forrigeSpørsmålId: String?,
) {
    enum class StegStatus {
        NYTT_SPØRSMÅL,
        FERDIG,
    }
}
