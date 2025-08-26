package no.nav.fia.arbeidsgiver.proxy.samarbeid

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

@Serializable
data class SamarbeidMedDokumenterDto(
    val id: Int, // TODO: uuid etc...
    val navn: String,
    val status: Status,
    val sistEndret: LocalDateTime? = null,
    val dokumenter: List<DokumentMetadata> = emptyList(),
) {
    companion object {
        enum class Status {
            AKTIV,
            FULLFØRT,
            SLETTET,
            AVBRUTT,
        }
    }
}
