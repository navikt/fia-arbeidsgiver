package no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto

import kotlinx.serialization.Serializable
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.Spørsmål

@Serializable
data class SpørsmålDto(
    val id: String,
    val tekst: String,
    val flervalg: Boolean,
    val svaralternativer: List<SvaralternativDto>,
    val kategori: String,
)

fun Spørsmål.tilDto() =
    SpørsmålDto(
        id = id.toString(),
        tekst = tekst,
        flervalg = flervalg,
        svaralternativer = svaralternativer.map { it.tilDto() },
        kategori = kategori,
    )
