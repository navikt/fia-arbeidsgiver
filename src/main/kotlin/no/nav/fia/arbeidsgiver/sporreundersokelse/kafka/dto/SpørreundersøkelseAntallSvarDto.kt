package no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.dto

import kotlinx.serialization.Serializable

@Serializable
data class SpørreundersøkelseAntallSvarDto(
	val spørreundersøkelseId: String,
	val spørsmålId: String,
	val antallSvar: Int,
)
