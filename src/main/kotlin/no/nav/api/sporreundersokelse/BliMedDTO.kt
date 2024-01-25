package no.nav.api.sporreundersokelse

import kotlinx.serialization.Serializable

@Serializable
data class BliMedDTO (
    val spørreundersøkelseId: String,
    val sesjonsId: String,
)