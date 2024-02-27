package no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class BliMedDTO (
    val spørreundersøkelseId: String,
    val sesjonsId: String,
)