package no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto

import no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.dto.SvaralternativDto

data class SpørsmålsoversiktDto(
	val spørsmålTekst: String,
	val svaralternativer: List<SvaralternativDto>,
	val nesteId: String?,
	val nesteType: String,
	val forrigeId: String?,
	val forrigeType: String
)
