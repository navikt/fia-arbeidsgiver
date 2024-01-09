package no.nav.api.kartlegging

import kotlinx.serialization.Serializable

@Serializable
data class BliMedDTO (
    val id: String,
    val sesjonsId: String,
)