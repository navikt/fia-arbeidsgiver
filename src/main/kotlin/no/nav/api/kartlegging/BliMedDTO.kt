package no.nav.api.kartlegging

import kotlinx.serialization.Serializable

@Serializable
data class BliMedDTO (
    val spørreundersøkelseId: String,
    val sesjonsId: String,
)