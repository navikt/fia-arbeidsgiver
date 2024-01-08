package no.nav.api.kartlegging

import kotlinx.serialization.Serializable

@Serializable
data class SpørreundersøkelseDTO (
    val id: String,
    val sesjonsId: String,
)