package no.nav.fia.arbeidsgiver.sporreundersokelse.kafka

import kotlinx.serialization.Serializable
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.Tema

@Serializable
data class TemaMedSpørsmålOgSvaralternativerDto(
	val temanavn: Tema,
	val spørsmålOgSvaralternativer: List<SpørsmålOgSvaralternativerDto>
)