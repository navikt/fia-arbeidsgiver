package no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.dto

import ia.felles.integrasjoner.kafkameldinger.Temanavn
import kotlinx.serialization.Serializable
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.TemaMedSpørsmålOgSvaralternativer

@Serializable
data class TemaMedSpørsmålOgSvaralternativerDto(
    val temaId: Int,
    val temanavn: Temanavn,
    val beskrivelse: String,
    val introtekst: String,
    val spørsmålOgSvaralternativer: List<SpørsmålOgSvaralternativerDto>,
)

fun TemaMedSpørsmålOgSvaralternativerDto.tilDomene() = TemaMedSpørsmålOgSvaralternativer(
    temaId = temaId,
    temanavn = temanavn,
    beskrivelse = beskrivelse,
    introtekst = introtekst,
    spørsmålOgSvaralternativer = spørsmålOgSvaralternativer.map { it.tilDomene() }
)
