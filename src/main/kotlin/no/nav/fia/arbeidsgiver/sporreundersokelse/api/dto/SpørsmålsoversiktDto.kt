package no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto

import kotlinx.serialization.Serializable
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.SpørsmålOgSvaralternativer
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

fun SpørsmålOgSvaralternativer.tilSpørsmålsoversiktDto(
	nesteSpørsmålId: String?,
	forrigeSpørsmålId: String?,
) = SpørsmålsoversiktDto(
	spørsmålTekst = spørsmål,
	svaralternativer = svaralternativer.map { SvaralternativDto(
		svarId = it.svarId.toString(),
		svartekst = it.svartekst
	)
	},
	nesteId = nesteSpørsmålId,
	nesteType = if (nesteSpørsmålId != null ) "SPØRSMÅL" else "FERDIG",
	forrigeId = forrigeSpørsmålId,
	forrigeType = if (forrigeSpørsmålId != null ) "SPØRSMÅL" else "OVERSIKT",
)