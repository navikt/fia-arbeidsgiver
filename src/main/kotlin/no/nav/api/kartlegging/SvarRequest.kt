package no.nav.api.kartlegging

import kotlinx.serialization.Serializable

@Serializable
data class SvarRequest (
    val spørreundersøkelseId: String,
    val spørsmålId: String,
    val svarId: String
)
