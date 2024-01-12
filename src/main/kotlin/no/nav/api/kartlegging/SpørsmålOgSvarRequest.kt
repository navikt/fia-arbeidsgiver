package no.nav.api.kartlegging

import kotlinx.serialization.Serializable

@Serializable
data class SpørsmålOgSvarRequest(
    val spørreundersøkelseId: String,
    val sesjonsId: String
)