package no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto

import kotlinx.serialization.Serializable
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.Tema

@Serializable
data class StartTemaRequest (
	val spørreundersøkelseId: String,
	val vertId: String,
	val tema: Tema,
)