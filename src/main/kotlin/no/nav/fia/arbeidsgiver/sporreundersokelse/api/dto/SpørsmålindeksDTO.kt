package no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class SpørsmålindeksDTO(
    val spørreundersøkelseId: String,
    val indeks: Int
)
