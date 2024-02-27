package no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto

import kotlinx.serialization.Serializable
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.Kategori

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
}