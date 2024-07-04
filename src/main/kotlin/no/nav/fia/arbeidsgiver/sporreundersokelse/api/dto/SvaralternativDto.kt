package no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto

import kotlinx.serialization.Serializable
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.Svaralternativ

@Serializable
data class SvaralternativDto(
    val svarId: String,
    val svartekst: String,
)

fun Svaralternativ.tilDto() = SvaralternativDto(
    svarId = id.toString(),
    svartekst = svartekst
)
