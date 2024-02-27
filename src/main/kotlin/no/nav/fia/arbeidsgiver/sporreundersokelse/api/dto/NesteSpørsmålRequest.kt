package no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class NesteSpørsmålRequest(
    val spørreundersøkelseId: String,
    val sesjonsId: String,
    val nåværrendeSpørsmålId: String,
)