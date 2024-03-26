package no.nav.fia.arbeidsgiver.sporreundersokelse.api.vert.dto

import kotlinx.serialization.Serializable
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.Temanavn
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.TemaMedSpørsmålOgSvaralternativer

@Serializable
data class TemaOversiktDto(
    val temaId: Int,
    val temanavn: Temanavn,
    val del: Int, // starter fra 1 (er ikke en list-index)
    val tittel: String,
    val beskrivelse: String,
    val introtekst: String,
    val status: TemaStatus,
    val førsteSpørsmålId: String,
)



fun TemaMedSpørsmålOgSvaralternativer.tilTemaOversiktDto(temaStatus: List<TemaSvarStatus>, del: Int):TemaOversiktDto {
    val temaIndex = temaStatus.indexOfFirst { it.temaId == temaId }
    val temaIdTilForrigeTema = if (temaIndex > 0) temaStatus[temaIndex].temaId else -1

    val status = when {
        temaStatus.any { it.temaId == temaId && it.harÅpnetAlleSpørsmål } -> TemaStatus.ALLE_SPØRSMÅL_ÅPNET
        temaStatus.any { it.temaId == temaIdTilForrigeTema && it.harÅpnetAlleSpørsmål } -> TemaStatus.ÅPNET
        temaIndex == 0 -> TemaStatus.ÅPNET
        else -> TemaStatus.IKKE_ÅPNET
    }

    return TemaOversiktDto(
        temaId = temaId,
        temanavn = temanavn,
        del = del,
        tittel = temanavn.name,
        beskrivelse = beskrivelse,
        introtekst = introtekst,
        status = status,
        førsteSpørsmålId = spørsmålOgSvaralternativer.first().id.toString()
    )
}

fun List<TemaMedSpørsmålOgSvaralternativer>.tilTemaOversiktDto(temaStatus: List<TemaSvarStatus>): List<TemaOversiktDto> =
    mapIndexed { index, temaMedSpørsmålOgSvaralternativer ->
        temaMedSpørsmålOgSvaralternativer.tilTemaOversiktDto(temaStatus = temaStatus, del = index + 1)
    }