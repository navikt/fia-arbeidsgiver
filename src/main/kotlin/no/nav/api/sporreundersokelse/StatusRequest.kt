package no.nav.api.sporreundersokelse

import kotlinx.serialization.Serializable

@Serializable
data class StatusRequest(
    val spørreundersøkelseId: String,
    val sesjonsId: String,
)
