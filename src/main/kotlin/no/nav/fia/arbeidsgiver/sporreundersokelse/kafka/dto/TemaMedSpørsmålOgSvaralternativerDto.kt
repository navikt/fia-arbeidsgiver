package no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.dto

import kotlinx.serialization.Serializable
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.Tema

@Serializable
data class TemaMedSpørsmålOgSvaralternativerDto(
	val temanavn: Tema,
	val beskrivelse: String,
	val introtekst: String,
	val spørsmålOgSvaralternativer: List<SpørsmålOgSvaralternativerDto>
)