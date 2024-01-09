package no.nav.domene.kartlegging

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import java.util.UUID
import no.nav.util.UUIDSerializer

@Serializable
data class SpørsmålOgSvaralternativer (
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val spørsmål: String,
    val svaralternativer: List<Svaralternativ>
)

@Serializable
data class Svaralternativ (
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val tekst: String
)

@Serializable
data class Spørreundersøkelse (
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val pinKode: String,
    val spørsmålOgSvaralternativer: List<SpørsmålOgSvaralternativer>,
    val status: String,
    val avslutningsdato: LocalDate
)