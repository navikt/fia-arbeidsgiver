package no.nav.fia.arbeidsgiver.api.sporreundersokelse

import kotlinx.serialization.Serializable
import no.nav.fia.arbeidsgiver.util.UUIDSerializer
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
