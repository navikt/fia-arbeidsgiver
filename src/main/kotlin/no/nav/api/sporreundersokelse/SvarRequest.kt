package no.nav.api.sporreundersokelse

import kotlinx.serialization.Serializable

@Serializable
data class SvarRequest (
    val spørreundersøkelseId: String,
    val sesjonsId: String,
    val spørsmålId: String,
    val svarId: String
)
