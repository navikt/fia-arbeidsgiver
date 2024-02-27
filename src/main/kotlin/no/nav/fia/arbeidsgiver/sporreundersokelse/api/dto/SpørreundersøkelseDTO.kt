package no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto

import kotlinx.serialization.Serializable
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.SpørsmålOgSvaralternativer
import java.util.UUID
import no.nav.fia.arbeidsgiver.sporreundersokelse.util.UUIDSerializer

@Serializable
data class SpørsmålOgSvaralternativerDTO (
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val spørsmål: String,
    val svaralternativer: List<SvaralternativDTO>
) {
    companion object {
        fun toDto(spørsmålOgSvaralternativer: List<SpørsmålOgSvaralternativer>) =
            spørsmålOgSvaralternativer.map { it.toDto() }
    }
}

@Serializable
data class SvaralternativDTO (
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val tekst: String
)
