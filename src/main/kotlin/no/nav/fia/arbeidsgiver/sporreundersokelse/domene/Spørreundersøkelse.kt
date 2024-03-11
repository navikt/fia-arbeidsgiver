package no.nav.fia.arbeidsgiver.sporreundersokelse.domene

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import java.util.UUID
import no.nav.fia.arbeidsgiver.sporreundersokelse.util.UUIDSerializer

@Serializable
data class Spørreundersøkelse (
    @Serializable(with = UUIDSerializer::class)
    val spørreundersøkelseId: UUID,
    @Serializable(with = UUIDSerializer::class)
    val vertId: UUID,
    val spørsmålOgSvaralternativer: List<SpørsmålOgSvaralternativer>,
    val status: SpørreundersøkelseStatus,
    val type: String,
    val avslutningsdato: LocalDate
)

