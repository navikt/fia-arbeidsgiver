package no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto

import kotlinx.serialization.Serializable
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.Spørreundersøkelse
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.TemaStatus

@Serializable
data class TemaDto(
    val id: Int,
    val del: Int,
    val navn: String,
    val status: TemaStatus,
    val førsteSpørsmålId: String,
    val spørsmålOgSvaralternativer: List<SpørsmålDto>,
)

fun Spørreundersøkelse.tilTemaDto(temaId: Int, temaStatus: List<TemaSvarStatus>): TemaDto {
    //TODO: Refactor.
    //TODO: Ikke map gjennom alle temaer og lag liste av TemaSvarStatus, men heller hent kun status for gjeldende tema
    // vi har TemaId det gjelder for, så tilTemaDto trenger ikke ta inn. Id
    // TO typer tilTemaDto.
    val indeksGjeldendeTema = this.temaer.indexOfFirst { it.id == temaId }
    val temaIdTilForrigeTema = if (indeksGjeldendeTema > 0) temaStatus[indeksGjeldendeTema - 1].temaId else -1
    val status = when {
        temaStatus.any { it.temaId == temaId && it.harÅpnetAlleSpørsmål } -> TemaStatus.ALLE_SPØRSMÅL_ÅPNET
        temaStatus.any { it.temaId == temaIdTilForrigeTema && it.harÅpnetAlleSpørsmål } -> TemaStatus.ÅPNET
        indeksGjeldendeTema == 0 -> TemaStatus.ÅPNET
        else -> TemaStatus.IKKE_ÅPNET
    }
    val gjeldendeTema = this.temaer.first { it.id == temaId }

    return TemaDto(
        id = gjeldendeTema.id,
        del = indeksGjeldendeTema + 1,
        navn = gjeldendeTema.navn ?: gjeldendeTema.beskrivelse!!,
        status = status,
        førsteSpørsmålId = gjeldendeTema.spørsmål.first().id.toString(),
        spørsmålOgSvaralternativer = gjeldendeTema.spørsmål.map { it.tilDto() }
    )
}

fun Spørreundersøkelse.tilTemaDtoer(temaStatus: List<TemaSvarStatus>) =
    temaer.map { tilTemaDto(temaId = it.id, temaStatus = temaStatus) }