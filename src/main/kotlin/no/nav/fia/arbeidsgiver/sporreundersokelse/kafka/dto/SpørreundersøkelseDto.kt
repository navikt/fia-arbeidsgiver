package no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.dto

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.SpørreundersøkelseStatus

@Serializable
data class SpørreundersøkelseDto (
    val spørreundersøkelseId: String,
    val vertId: String,
    val status: SpørreundersøkelseStatus,
    val type: String,
    val temaMedSpørsmålOgSvaralternativer: List<TemaMedSpørsmålOgSvaralternativerDto>,
    val avslutningsdato: LocalDate
)