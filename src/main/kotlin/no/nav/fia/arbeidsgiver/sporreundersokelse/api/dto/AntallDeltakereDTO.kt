package no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto

import kotlinx.serialization.Serializable

@Deprecated("Skal erstattes med AntallSvarPerSpørsmålDTO")
@Serializable
data class AntallDeltakereDTO(
    val spørreundersøkelseId: String,
    val antallDeltakere: Int,
    val antallSvar: List<AntallSvarDTO>,
)


@Deprecated("Skal erstattes med AntallSvarPerSpørsmålDTO")
@Serializable
data class AntallSvarDTO(
    val spørsmålId: String,
    val antall: Int,
)

@Serializable
data class AntallSvarPerSpørsmålDTO(
    val antallDeltakere: Int,
    val antallSvar: Int,
)
