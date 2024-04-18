package no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.dto

import kotlinx.serialization.Serializable

@Serializable
data class SpørreundersøkelseOppdateringNøkkel(
    val spørreundersøkelseId: String,
    val oppdateringsType: OppdateringsType,
)

enum class OppdateringsType {
    RESULTATER_FOR_TEMA,
    ANTALL_SVAR
}