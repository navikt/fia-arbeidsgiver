package no.nav.api.sporreundersokelse

import kotlinx.serialization.Serializable

@Serializable
data class BliMedRequest (
    val spørreundersøkelseId: String,
)
