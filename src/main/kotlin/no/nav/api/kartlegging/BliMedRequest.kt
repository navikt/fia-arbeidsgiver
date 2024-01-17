package no.nav.api.kartlegging

import kotlinx.serialization.Serializable

@Serializable
data class BliMedRequest (
    val spørreundersøkelseId: String,
)
