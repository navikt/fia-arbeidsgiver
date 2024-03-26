package no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class IdentifiserbartSpørsmål(val temaId: Int, val spørsmålId: String)