package no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class SvarRequest(
    val svarIder: List<String>,
)
