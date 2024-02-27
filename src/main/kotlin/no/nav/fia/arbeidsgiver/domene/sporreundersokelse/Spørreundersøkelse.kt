package no.nav.fia.arbeidsgiver.domene.sporreundersokelse

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import no.nav.fia.arbeidsgiver.api.sporreundersokelse.Kategori
import no.nav.fia.arbeidsgiver.api.sporreundersokelse.SpørsmålOgSvaralternativerDTO
import no.nav.fia.arbeidsgiver.api.sporreundersokelse.SpørsmålOgSvaralternativerTilFrontendDTO
import no.nav.fia.arbeidsgiver.api.sporreundersokelse.SvaralternativDTO
import java.util.UUID
import no.nav.fia.arbeidsgiver.util.UUIDSerializer

@Serializable
data class SpørsmålOgSvaralternativer (
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val kategori: Kategori,
    val spørsmål: String,
    val antallSvar: Int = 0,
    val svaralternativer: List<Svaralternativ>
) {
    fun toDto(): SpørsmålOgSvaralternativerDTO =
        SpørsmålOgSvaralternativerDTO(
            id = this.id,
            spørsmål = this.spørsmål,
            svaralternativer = this.svaralternativer.map { svaralternativ ->
                SvaralternativDTO(
                    id = svaralternativ.svarId,
                    tekst = svaralternativ.svartekst
                )
            }
        )

    fun toFrontendDto(indeksTilSpørsmål: Int, indeksTilSisteSpørsmål: Int): SpørsmålOgSvaralternativerTilFrontendDTO =
        SpørsmålOgSvaralternativerTilFrontendDTO(
            id = this.id,
            spørsmålIndeks = indeksTilSpørsmål,
            sisteSpørsmålIndeks = indeksTilSisteSpørsmål,
            spørsmål = this.spørsmål,
            svaralternativer = this.svaralternativer.map { svaralternativ ->
                SvaralternativDTO(
                    id = svaralternativ.svarId,
                    tekst = svaralternativ.svartekst
                )
            }
        )
}

@Serializable
data class Svaralternativ (
    @Serializable(with = UUIDSerializer::class)
    val svarId: UUID,
    val svartekst: String
)

@Serializable
data class Spørreundersøkelse (
    @Serializable(with = UUIDSerializer::class)
    val spørreundersøkelseId: UUID,
    @Serializable(with = UUIDSerializer::class)
    val vertId: UUID? = null,
    val spørsmålOgSvaralternativer: List<SpørsmålOgSvaralternativer>,
    val status: SpørreundersøkelseStatus,
    val type: String,
    val avslutningsdato: LocalDate
)

enum class SpørreundersøkelseStatus {
    OPPRETTET, PÅBEGYNT, AVSLUTTET
}
