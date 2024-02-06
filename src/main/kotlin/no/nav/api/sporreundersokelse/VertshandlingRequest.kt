package no.nav.api.sporreundersokelse

import kotlinx.serialization.Serializable

@Serializable
data class VertshandlingRequest(
    val spørreundersøkelseId: String,
    val vertId: String
)
