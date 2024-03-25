package no.nav.fia.arbeidsgiver.sporreundersokelse.api.vert.dto

import kotlinx.serialization.Serializable
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.IdentifiserbartSpørsmål
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.Tema

@Serializable
data class TemaOversiktDto(
	val temaId: Tema,
	val tittel: String,
	val beskrivelse: String,
	val introtekst: String,
	val førsteSpørsmålId: String,
) {
	fun tilIdentifiserbartSpørsmål() = IdentifiserbartSpørsmål(
		tema = temaId,
		spørsmålId = førsteSpørsmålId
	)
}