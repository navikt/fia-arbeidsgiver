package no.nav.api.sporreundersokelse

import kotlinx.serialization.Serializable

@Serializable
data class SpørsmålOgSvarRequest(
    val spørreundersøkelseId: String,
    val sesjonsId: String
)