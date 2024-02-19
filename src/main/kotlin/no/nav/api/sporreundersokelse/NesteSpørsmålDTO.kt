package no.nav.api.sporreundersokelse

import kotlinx.serialization.Serializable

enum class NesteSpøsmålStatus {
    ER_SISTE_SPØRSMÅL,
    OK
}

@Serializable
data class NesteSpørsmålDTO(
    val status: NesteSpøsmålStatus,
    val nesteId: String? = null,
)
