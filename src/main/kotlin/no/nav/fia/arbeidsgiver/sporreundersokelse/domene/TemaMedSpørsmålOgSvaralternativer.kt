package no.nav.fia.arbeidsgiver.sporreundersokelse.domene

import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class TemaMedSpørsmålOgSvaralternativer(
	val tema: Tema,
	val beskrivelse: String,
	val introtekst: String,
	val spørsmålOgSvaralternativer: List<SpørsmålOgSvaralternativer>
) {
	fun finnSpørsmål(spørsmålId: UUID) = spørsmålOgSvaralternativer.find { it.id == spørsmålId }
}