package no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class SvarRequest (
    val spørreundersøkelseId: String,
    val sesjonsId: String,
    val spørsmålId: String,
    val svarId: String
)
