package no.nav.fia.arbeidsgiver.api.sporreundersokelse

import kotlinx.serialization.Serializable

@Serializable
data class KategoristatusDTO(
    val kategori: Kategori,
    val status: Status,
    val spørsmålindeks: Int? = null,
    val antallSpørsmål: Int? = null,
) {

    enum class Status {
        OPPRETTET,
        IKKE_PÅBEGYNT,
        PÅBEGYNT,
    }
}