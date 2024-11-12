package no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.evaluering

import kotlinx.serialization.Serializable

@Serializable
data class PlanTemaDto(
    val id: Int,
    val navn: String,
    val inkludert: Boolean,
    val undertemaer: List<PlanUndertemaDto>,
)
