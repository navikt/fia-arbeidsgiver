package no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.dto

import ia.felles.integrasjoner.kafkameldinger.SpørreundersøkelseStatus
import java.util.*
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.Spørreundersøkelse

@Serializable
data class SpørreundersøkelseDto(
    val spørreundersøkelseId: String,
    val vertId: String,
    val orgnummer: String,
    val virksomhetsNavn: String,
    val status: SpørreundersøkelseStatus,
    val type: String,
    val temaMedSpørsmålOgSvaralternativer: List<TemaMedSpørsmålOgSvaralternativerDto>,
    val avslutningsdato: LocalDate,
)

fun SpørreundersøkelseDto.tilDomene() = Spørreundersøkelse(
    spørreundersøkelseId = UUID.fromString(spørreundersøkelseId),
    vertId = UUID.fromString(vertId),
    orgnummer = orgnummer,
    virksomhetsNavn = virksomhetsNavn,
    temaMedSpørsmålOgSvaralternativer = temaMedSpørsmålOgSvaralternativer.map { it.tilDomene() },
    status = status,
    type = type,
    avslutningsdato = avslutningsdato,
)
