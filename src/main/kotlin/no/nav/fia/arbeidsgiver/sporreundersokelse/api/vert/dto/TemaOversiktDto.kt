package no.nav.fia.arbeidsgiver.sporreundersokelse.api.vert.dto

import ia.felles.integrasjoner.kafkameldinger.Temanavn
import kotlinx.serialization.Serializable
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.Spørreundersøkelse
import no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.dto.SpørsmålOgSvaralternativerDto
import no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.dto.tilDto

@Serializable
data class TemaOversiktDto(
    val temaId: Int,
    val nesteTemaId: Int?,
    val temanavn: Temanavn,
    val del: Int, // starter fra 1 (er ikke en list-index)
    val tittel: String,
    val beskrivelse: String,
    val introtekst: String,
    val status: TemaStatus,
    val førsteSpørsmålId: String,
    val spørsmålOgSvaralternativer: List<SpørsmålOgSvaralternativerDto>,
)

fun Spørreundersøkelse.tilTemaOversiktDto(temaId: Int, temaStatus: List<TemaSvarStatus>): TemaOversiktDto {
    val indeksGjeldendeTema = this.temaMedSpørsmålOgSvaralternativer.indexOfFirst { it.temaId == temaId }
    val temaIdTilForrigeTema = if (indeksGjeldendeTema > 0) temaStatus[indeksGjeldendeTema - 1].temaId else -1
    val status = when {
        temaStatus.any { it.temaId == temaId && it.harÅpnetAlleSpørsmål } -> TemaStatus.ALLE_SPØRSMÅL_ÅPNET
        temaStatus.any { it.temaId == temaIdTilForrigeTema && it.harÅpnetAlleSpørsmål } -> TemaStatus.ÅPNET
        indeksGjeldendeTema == 0 -> TemaStatus.ÅPNET
        else -> TemaStatus.IKKE_ÅPNET
    }
    val gjeldendeTema = this.temaMedSpørsmålOgSvaralternativer.first { it.temaId == temaId }
    val nesteTemaId = this.temaMedSpørsmålOgSvaralternativer.getOrNull(indeksGjeldendeTema + 1)?.temaId

    return TemaOversiktDto(
        temaId = gjeldendeTema.temaId,
        temanavn = gjeldendeTema.temanavn,
        del = indeksGjeldendeTema + 1,
        tittel = gjeldendeTema.temanavn.name,
        beskrivelse = gjeldendeTema.beskrivelse,
        introtekst = gjeldendeTema.introtekst,
        status = status,
        førsteSpørsmålId = gjeldendeTema.spørsmålOgSvaralternativer.first().id.toString(),
        nesteTemaId = nesteTemaId,
        spørsmålOgSvaralternativer = gjeldendeTema.spørsmålOgSvaralternativer.map { it.tilDto() }
    )
}

fun Spørreundersøkelse.tilTemaOversiktDtoer(temaStatus: List<TemaSvarStatus>) =
    temaMedSpørsmålOgSvaralternativer.map { tilTemaOversiktDto(temaId = it.temaId, temaStatus = temaStatus) }