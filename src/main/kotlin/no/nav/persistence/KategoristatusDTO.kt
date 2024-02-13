package no.nav.persistence

import kotlinx.serialization.Serializable

@Serializable
data class KategoristatusDTO(
    val kategori: Kategori,
    val status: Status,
    val spørsmålindeks: Int? = null
) {

    enum class Status {
        OPPRETTET,
        IKKE_PÅBEGYNT,
        PÅBEGYNT,
    }

    enum class Kategori {
        PARTSSAMARBEID
    }
}