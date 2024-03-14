package no.nav.fia.arbeidsgiver.sporreundersokelse.api.deltaker.dto

import kotlinx.serialization.Serializable
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.Tema

@Serializable
data class StartDto(
	val temaId: Tema,
	val spørsmålId: String,
)
