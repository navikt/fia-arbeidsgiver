package no.nav.api.sporreundersokelse

import kotlinx.serialization.Serializable

@Serializable
data class SpørsmålOgSvaralternativerRequest(
    val spørreundersøkelseId: String,
    val sesjonsId: String
)