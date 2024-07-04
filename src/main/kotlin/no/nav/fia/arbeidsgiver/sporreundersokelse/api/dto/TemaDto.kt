package no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto

import kotlinx.serialization.Serializable
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.Spørreundersøkelse
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.TemaStatus

@Serializable
data class TemaDto(
    val temaId: Int,
    val nesteTemaId: Int?,
    val navn: String,
    val del: Int, // starter fra 1 (er ikke en list-index)
    val status: TemaStatus,
    val førsteSpørsmålId: String,
    val spørsmålOgSvaralternativer: List<SpørsmålDto>,
)

fun Spørreundersøkelse.hentTemaDto(temaId: Int, temaStatus: List<TemaSvarStatus>): TemaDto {
    val indeksGjeldendeTema = this.temaer.indexOfFirst { it.id == temaId }
    val temaIdTilForrigeTema = if (indeksGjeldendeTema > 0) temaStatus[indeksGjeldendeTema - 1].temaId else -1
    val status = when {
        temaStatus.any { it.temaId == temaId && it.harÅpnetAlleSpørsmål } -> TemaStatus.ALLE_SPØRSMÅL_ÅPNET
        temaStatus.any { it.temaId == temaIdTilForrigeTema && it.harÅpnetAlleSpørsmål } -> TemaStatus.ÅPNET
        indeksGjeldendeTema == 0 -> TemaStatus.ÅPNET
        else -> TemaStatus.IKKE_ÅPNET
    }
    val gjeldendeTema = this.temaer.first { it.id == temaId }
    val nesteTemaId = this.temaer.getOrNull(indeksGjeldendeTema + 1)?.id

    return TemaDto(
        temaId = gjeldendeTema.id,
        del = indeksGjeldendeTema + 1,
        navn = gjeldendeTema.navn ?: gjeldendeTema.beskrivelse!!,
        status = status,
        førsteSpørsmålId = gjeldendeTema.spørsmål.first().id.toString(),
        nesteTemaId = nesteTemaId,
        spørsmålOgSvaralternativer = gjeldendeTema.spørsmål.map { it.tilDto() }
    )
}

fun Spørreundersøkelse.tilTemaOversiktDtoer(temaStatus: List<TemaSvarStatus>) =
    temaer.map { hentTemaDto(temaId = it.id, temaStatus = temaStatus) }