package no.nav.kafka

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

@Serializable
data class IASakStatus(
    val orgnr: String,
    val saksnummer: String,
    val status: String,
    val sistOppdatert: LocalDateTime,
)
