package no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto

import kotlinx.serialization.Serializable
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.Tema

@Serializable
data class TemastatusDTO(
    val tema: Tema,
    val status: Status,
    val spørsmålindeks: Int = -1,
    val antallSpørsmål: Int = 0,
) {

    enum class Status {
        OPPRETTET,
        IKKE_PÅBEGYNT,
        PÅBEGYNT,
    }
}