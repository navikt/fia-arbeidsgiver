package no.nav.domene.sporreundersokelse

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import java.util.UUID
import no.nav.util.UUIDSerializer

@Serializable
data class SpørsmålOgSvaralternativer (
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val kategori: String,
    val spørsmål: String,
    val antallSvar: Int = 0,
    val svaralternativer: List<Svaralternativ>
)

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
    OPPRETTET, AVSLUTTET
}
