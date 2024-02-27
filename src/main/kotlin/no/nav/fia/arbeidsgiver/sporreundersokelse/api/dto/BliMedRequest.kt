package no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class BliMedRequest (
    val spørreundersøkelseId: String,
)
