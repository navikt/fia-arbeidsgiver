package no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto

import kotlinx.serialization.Serializable
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.Kategori

@Serializable
data class StarteKategoriRequest (
	val spørreundersøkelseId: String,
	val vertId: String,
	val kategori: Kategori,
)