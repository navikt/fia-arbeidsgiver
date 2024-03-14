package no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto

import kotlinx.serialization.Serializable
import no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.dto.SvaralternativDto

@Serializable
data class SpørsmålsoversiktDto(
	val spørsmålTekst: String,
	val svaralternativer: List<SvaralternativDto>,
	val nesteId: String?,
	val nesteType: String,
	val forrigeId: String?,
	val forrigeType: String
)
