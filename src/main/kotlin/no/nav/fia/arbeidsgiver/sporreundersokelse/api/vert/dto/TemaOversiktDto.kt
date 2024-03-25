package no.nav.fia.arbeidsgiver.sporreundersokelse.api.vert.dto

import kotlinx.serialization.Serializable
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.IdentifiserbartSpørsmål
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.Tema
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.TemaMedSpørsmålOgSvaralternativer

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

fun TemaMedSpørsmålOgSvaralternativer.tilTemaOversiktDto() = TemaOversiktDto(
	temaId = tema,
	tittel = tema.name,
	beskrivelse = beskrivelse,
	introtekst = introtekst,
	førsteSpørsmålId = spørsmålOgSvaralternativer.first().id.toString()
)

fun List<TemaMedSpørsmålOgSvaralternativer>.tilTemaOversiktDto() = map { it.tilTemaOversiktDto() }