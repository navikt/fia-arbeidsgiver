package no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto

import kotlinx.serialization.Serializable
import no.nav.fia.arbeidsgiver.sporreundersokelse.util.UUIDSerializer
import java.util.*

@Serializable
data class SpørsmålOgSvaralternativerTilFrontendDTO (
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val spørsmålIndeks: Int,
    val sisteSpørsmålIndeks: Int,
    val spørsmål: String,
    val svaralternativer: List<SvaralternativDTO>
)
