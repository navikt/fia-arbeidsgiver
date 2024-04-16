package no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.dto

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.Spørreundersøkelse
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.SpørreundersøkelseStatus
import java.util.*

@Serializable
data class SpørreundersøkelseDto (
    val spørreundersøkelseId: String,
    val vertId: String,
    val status: SpørreundersøkelseStatus,
    val type: String,
    val temaMedSpørsmålOgSvaralternativer: List<TemaMedSpørsmålOgSvaralternativerDto>,
    val avslutningsdato: LocalDate
)

fun SpørreundersøkelseDto.tilDomene() = Spørreundersøkelse(
    spørreundersøkelseId = UUID.fromString(spørreundersøkelseId),
    vertId = UUID.fromString(vertId),
    temaMedSpørsmålOgSvaralternativer = temaMedSpørsmålOgSvaralternativer.map { it.tilDomene() },
    status = status,
    type = type,
    avslutningsdato = avslutningsdato,
)
