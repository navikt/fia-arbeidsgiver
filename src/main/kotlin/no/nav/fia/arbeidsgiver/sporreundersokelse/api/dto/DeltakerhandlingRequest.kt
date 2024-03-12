package no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto

import kotlinx.serialization.Serializable
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.Tema

@Serializable
data class DeltakerhandlingRequest(
	val spørreundersøkelseId: String,
	val sesjonsId: String,
	val tema: Tema = Tema.UTVIKLE_PARTSSAMARBEID,
)
