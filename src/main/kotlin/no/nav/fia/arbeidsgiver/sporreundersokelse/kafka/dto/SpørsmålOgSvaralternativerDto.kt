package no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.dto

import kotlinx.serialization.Serializable
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.SpørsmålOgSvaralternativer
import java.util.*

@Serializable
data class SpørsmålOgSvaralternativerDto(
    val id: String,
    val spørsmål: String,
    val svaralternativer: List<SvaralternativDto>,
    val flervalg: Boolean,
)

fun SpørsmålOgSvaralternativerDto.tilDomene() = SpørsmålOgSvaralternativer(
    id = UUID.fromString(id),
    spørsmål = spørsmål,
    svaralternativer = svaralternativer.map { it.tilDomene() },
    flervalg = flervalg
)

fun SpørsmålOgSvaralternativer.tilDto() = SpørsmålOgSvaralternativerDto(
    id = id.toString(),
    spørsmål = spørsmål,
    svaralternativer = svaralternativer.map { it.tilDto() },
    flervalg = flervalg
)
