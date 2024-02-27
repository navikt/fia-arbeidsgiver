package no.nav.fia.arbeidsgiver.sporreundersokelse.domene

import kotlinx.serialization.Serializable
import no.nav.fia.arbeidsgiver.sporreundersokelse.util.UUIDSerializer
import java.util.*

@Serializable
data class Svaralternativ (
	@Serializable(with = UUIDSerializer::class)
    val svarId: UUID,
	val svartekst: String
)