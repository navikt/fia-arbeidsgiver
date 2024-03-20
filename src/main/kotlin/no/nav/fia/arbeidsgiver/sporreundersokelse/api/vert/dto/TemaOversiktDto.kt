package no.nav.fia.arbeidsgiver.sporreundersokelse.api.vert.dto

import kotlinx.serialization.Serializable
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.IdentifiserbartSpørsmål
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.Tema

@Serializable
data class TemaOversiktDto(
	val tittel: String,
	val temaId: Tema,
	val førsteSpørsmålId: String,
) {
	fun tilIdentifiserbartSpørsmål() = IdentifiserbartSpørsmål(
		tema = temaId,
		spørsmålId = førsteSpørsmålId
	)
}