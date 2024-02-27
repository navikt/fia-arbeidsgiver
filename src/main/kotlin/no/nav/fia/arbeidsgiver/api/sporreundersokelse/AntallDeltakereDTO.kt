package no.nav.fia.arbeidsgiver.api.sporreundersokelse

import kotlinx.serialization.Serializable

@Serializable
data class AntallDeltakereDTO(
    val spørreundersøkelseId: String,
    val antallDeltakere: Int,
    val antallSvar: List<AntallSvarDTO>,
)

@Serializable
data class AntallSvarDTO(
    val spørsmålId: String,
    val antall: Int,
)
