package no.nav.fia.arbeidsgiver.api.sporreundersokelse

import kotlinx.serialization.Serializable

@Serializable
data class SpørsmålindeksDTO(
    val spørreundersøkelseId: String,
    val indeks: Int
)
