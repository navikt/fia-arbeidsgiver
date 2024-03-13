package no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.dto

import kotlinx.serialization.Serializable

@Serializable
data class SvaralternativDto(
    val svarId: String,
    val svartekst: String,
)