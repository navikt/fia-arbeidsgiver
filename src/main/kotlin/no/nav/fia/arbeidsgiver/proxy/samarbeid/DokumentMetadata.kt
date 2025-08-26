package no.nav.fia.arbeidsgiver.proxy.samarbeid

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

@Serializable
data class DokumentMetadata(
    val dokumentId: String,
    val type: String,
    val dato: LocalDateTime,
)
