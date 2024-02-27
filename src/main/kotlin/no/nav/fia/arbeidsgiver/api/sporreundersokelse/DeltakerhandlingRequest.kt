package no.nav.fia.arbeidsgiver.api.sporreundersokelse

import kotlinx.serialization.Serializable

@Serializable
data class DeltakerhandlingRequest(
    val spørreundersøkelseId: String,
    val sesjonsId: String,
    val kategori: Kategori = Kategori.PARTSSAMARBEID,
)
