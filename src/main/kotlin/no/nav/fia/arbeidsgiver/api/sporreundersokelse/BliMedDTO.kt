package no.nav.fia.arbeidsgiver.api.sporreundersokelse

import kotlinx.serialization.Serializable

@Serializable
data class BliMedDTO (
    val spørreundersøkelseId: String,
    val sesjonsId: String,
)