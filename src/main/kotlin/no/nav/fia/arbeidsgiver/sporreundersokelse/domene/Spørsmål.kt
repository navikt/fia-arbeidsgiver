package no.nav.fia.arbeidsgiver.sporreundersokelse.domene

import java.util.UUID

data class Spørsmål(
    val id: UUID,
    val tekst: String,
    val flervalg: Boolean,
    val svaralternativer: List<Svaralternativ>,
)
