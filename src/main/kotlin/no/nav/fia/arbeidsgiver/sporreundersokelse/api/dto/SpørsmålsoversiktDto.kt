package no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto

import kotlinx.serialization.Serializable
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.Spørreundersøkelse
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.SpørsmålOgSvaralternativer
import no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.dto.SvaralternativDto

@Serializable
data class SpørsmålsoversiktDto(
	val spørsmålTekst: String,
	val svaralternativer: List<SvaralternativDto>,
	val nesteSpørsmål: IdentifiserbartSpørsmål?,
	val forrigeSpørsmål: IdentifiserbartSpørsmål?,
)

fun SpørsmålOgSvaralternativer.tilSpørsmålsoversiktDto(spørreundersøkelse: Spørreundersøkelse) =
	SpørsmålsoversiktDto(
		spørsmålTekst = spørsmål,
		svaralternativer = svaralternativer.map { SvaralternativDto(
			svarId = it.svarId.toString(),
			svartekst = it.svartekst
		)},
		nesteSpørsmål = spørreundersøkelse.hentNesteSpørsmålOgTema(tema, id),
		forrigeSpørsmål = spørreundersøkelse.hentForrigeSpørsmålOgTema(tema, id)
	)

