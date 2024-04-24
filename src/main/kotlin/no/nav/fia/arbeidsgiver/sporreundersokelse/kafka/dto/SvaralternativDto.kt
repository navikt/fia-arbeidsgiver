package no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.dto

import kotlinx.serialization.Serializable
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.Svaralternativ
import java.util.*

@Serializable
data class SvaralternativDto(
    val svarId: String,
    val svartekst: String,
)

fun SvaralternativDto.tilDomene() = Svaralternativ(
    svarId = UUID.fromString(svarId),
    svartekst = svartekst,
)

fun Svaralternativ.tilDto() = SvaralternativDto(
    svarId = svarId.toString(),
    svartekst = svartekst
)
