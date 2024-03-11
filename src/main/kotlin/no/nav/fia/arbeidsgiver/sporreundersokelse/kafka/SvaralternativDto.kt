package no.nav.fia.arbeidsgiver.sporreundersokelse.kafka

import kotlinx.serialization.Serializable

@Serializable
data class SvaralternativDto(
    val svarId: String,
    val svartekst: String,
)