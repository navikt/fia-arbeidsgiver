package no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto

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
	    NY_KATEGORI,
        FERDIG,
    }
}
