package no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto

import kotlinx.serialization.Serializable
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.Svaralternativ

@Serializable
data class SvaralternativDto(
    val id: String,
    val tekst: String,
)

fun Svaralternativ.tilDto() = SvaralternativDto(
    id = id.toString(),
    tekst = svartekst
)
