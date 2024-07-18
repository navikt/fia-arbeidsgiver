package no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class IdentifiserbartSpørsmålDto(val temaId: Int, val spørsmålId: String)