package no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto

import kotlinx.serialization.Serializable
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.Kategori

@Serializable
data class DeltakerhandlingRequest(
	val spørreundersøkelseId: String,
	val sesjonsId: String,
	val kategori: Kategori = Kategori.PARTSSAMARBEID,
)
