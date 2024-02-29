package no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto

import kotlinx.serialization.Serializable
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.Tema

@Serializable
data class VertshandlingRequest(
	val spørreundersøkelseId: String,
	val vertId: String,
	val tema: Tema = Tema.PARTSSAMARBEID,
)
