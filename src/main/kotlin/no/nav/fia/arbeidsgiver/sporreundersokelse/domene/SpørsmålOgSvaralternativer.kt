package no.nav.fia.arbeidsgiver.sporreundersokelse.domene

import java.util.*
import kotlinx.serialization.Serializable
import no.nav.fia.arbeidsgiver.sporreundersokelse.util.UUIDSerializer

@Serializable
data class SpørsmålOgSvaralternativer(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val spørsmål: String,
    val svaralternativer: List<Svaralternativ>,
    val flervalg: Boolean,
)