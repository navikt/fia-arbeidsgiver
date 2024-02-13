package no.nav.api.sporreundersokelse

import kotlinx.serialization.Serializable

@Serializable
data class StarteKategoriRequest (
    val spørreundersøkelseId: String,
    val vertId: String,
    val kategori: String,
)