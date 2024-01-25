package no.nav.api.sporreundersokelse

import kotlinx.serialization.Serializable
import java.util.UUID
import no.nav.domene.sporreundersokelse.SpørsmålOgSvaralternativer
import no.nav.util.UUIDSerializer

@Serializable
data class SpørsmålOgSvaralternativerDTO (
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val spørsmål: String,
    val svaralternativer: List<SvaralternativDTO>
) {
    companion object {
        fun toDto(spørsmålOgSvaralternativer: List<SpørsmålOgSvaralternativer>) =
            spørsmålOgSvaralternativer.map { spørsmålOgSvarAlt ->
                SpørsmålOgSvaralternativerDTO(
                    id = spørsmålOgSvarAlt.id,
                    spørsmål = spørsmålOgSvarAlt.spørsmål,
                    svaralternativer = spørsmålOgSvarAlt.svaralternativer.map { svaralternativ ->
                        SvaralternativDTO(
                            id = svaralternativ.id,
                            tekst = svaralternativ.tekst
                        )
                    }
                )
            }
    }
}

@Serializable
data class SvaralternativDTO (
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val tekst: String
)
