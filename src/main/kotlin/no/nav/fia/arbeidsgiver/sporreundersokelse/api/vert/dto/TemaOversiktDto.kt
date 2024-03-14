package no.nav.fia.arbeidsgiver.sporreundersokelse.api.vert.dto

import kotlinx.serialization.Serializable

@Serializable
data class TemaOversiktDto(
	val tittel: String,
	val temaId: String,
	val førsteSpørsmålId: String,
)