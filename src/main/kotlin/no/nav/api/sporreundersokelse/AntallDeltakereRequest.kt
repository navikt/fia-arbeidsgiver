package no.nav.api.sporreundersokelse

import kotlinx.serialization.Serializable

@Serializable
data class AntallDeltakereRequest(
    val spørreundersøkelseId: String
)
