package no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto

import kotlinx.serialization.Serializable
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.Spørreundersøkelse
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.SpørsmålOgSvaralternativer
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.temaFraSpørsmålId
import no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.dto.SvaralternativDto

@Serializable
data class SpørsmålsoversiktDto(
    val temabeskrivelse: String,
    val spørsmålTekst: String,
    val svaralternativer: List<SvaralternativDto>,
    val nesteSpørsmål: IdentifiserbartSpørsmål?,
    val forrigeSpørsmål: IdentifiserbartSpørsmål?,
)

fun SpørsmålOgSvaralternativer.tilSpørsmålsoversiktDto(spørreundersøkelse: Spørreundersøkelse) =
    SpørsmålsoversiktDto(
        temabeskrivelse = spørreundersøkelse.temaMedSpørsmålOgSvaralternativer.temaFraSpørsmålId(spørsmålId = id).beskrivelse,
        spørsmålTekst = spørsmål,
        svaralternativer = svaralternativer.map {
            SvaralternativDto(
                svarId = it.svarId.toString(),
                svartekst = it.svartekst
            )
        },
        nesteSpørsmål = spørreundersøkelse.hentNesteSpørsmålOgTema(id),
        forrigeSpørsmål = spørreundersøkelse.hentForrigeSpørsmålOgTema(id)
    )

