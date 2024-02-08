package no.nav.api.sporreundersokelse

import kotlinx.serialization.Serializable

@Serializable
data class AntallDeltakereDTO(
    val spørreundersøkelseId: String,
    val antallDeltakere: Int,
    val antallSvar: Map<String, Int>
)
