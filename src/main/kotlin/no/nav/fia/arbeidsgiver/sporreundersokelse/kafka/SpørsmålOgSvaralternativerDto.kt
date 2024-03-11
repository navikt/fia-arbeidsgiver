package no.nav.fia.arbeidsgiver.sporreundersokelse.kafka

import kotlinx.serialization.Serializable

@Serializable
data class SpørsmålOgSvaralternativerDto(
    val id: String,
    val spørsmål: String,
    val svaralternativer: List<SvaralternativDto>,
)