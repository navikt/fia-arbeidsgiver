package no.nav.fia.arbeidsgiver.sporreundersokelse.domene

import java.util.*

data class Spørsmål(
    val id: UUID,
    val tekst: String,
    val flervalg: Boolean,
    val svaralternativer: List<Svaralternativ>,
)