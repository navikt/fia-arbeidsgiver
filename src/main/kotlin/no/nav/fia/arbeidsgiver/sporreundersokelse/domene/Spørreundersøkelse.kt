package no.nav.fia.arbeidsgiver.sporreundersokelse.domene

import ia.felles.integrasjoner.kafkameldinger.SpørreundersøkelseStatus
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.IdentifiserbartSpørsmålDto
import java.util.UUID

data class Spørreundersøkelse(
    val id: UUID,
    val orgnummer: String,
    val virksomhetsNavn: String,
    val status: SpørreundersøkelseStatus,
    val type: String,
    val temaer: List<Tema>,
) {
    fun hentNesteSpørsmålOgTema(nåværendeSpørmålId: UUID): IdentifiserbartSpørsmålDto? {
        val gjeldendeTema = temaer.temaFraSpørsmålId(nåværendeSpørmålId)
        val gjeldeneTemaIdx = temaer.indexOfFirst { it.id == gjeldendeTema.id }
        val nesteSpørsmålIgjeldeneTema = gjeldendeTema.hentNesteSpørsmål(spørsmålId = nåværendeSpørmålId)
        return if (nesteSpørsmålIgjeldeneTema != null) {
            IdentifiserbartSpørsmålDto(
                temaId = gjeldendeTema.id,
                spørsmålId = nesteSpørsmålIgjeldeneTema.id.toString(),
            )
        } else {
            temaer.elementAtOrNull(gjeldeneTemaIdx + 1)?.let {
                IdentifiserbartSpørsmålDto(
                    temaId = it.id,
                    spørsmålId = it.spørsmål.first().id.toString()
                )
            }
        }
    }

    fun hentForrigeSpørsmålOgTema(nåværendeSpørmålId: UUID): IdentifiserbartSpørsmålDto? {
        val gjeldendeTema = temaer.temaFraSpørsmålId(nåværendeSpørmålId)
        val gjeldeneTemaIdx = temaer.indexOfFirst { it.id == gjeldendeTema.id }
        val forrigeSpørsmålIgjeldeneTema = gjeldendeTema.hentForrigeSpørsmål(spørsmålId = nåværendeSpørmålId)
        return if (forrigeSpørsmålIgjeldeneTema != null) {
            IdentifiserbartSpørsmålDto(
                temaId = gjeldendeTema.id,
                spørsmålId = forrigeSpørsmålIgjeldeneTema.id.toString(),
            )
        } else {
            temaer.elementAtOrNull(gjeldeneTemaIdx - 1)?.let {
                IdentifiserbartSpørsmålDto(
                    temaId = it.id,
                    spørsmålId = it.spørsmål.last().id.toString()
                )
            }
        }
    }

    fun hentSpørsmålITema(spørsmål: IdentifiserbartSpørsmålDto) =
        temaer.firstOrNull { it.id == spørsmål.temaId }?.let { tema ->
            val spørsmålIdx = tema.spørsmål.indexOfFirst { it.id == UUID.fromString(spørsmål.spørsmålId) }
            tema.spørsmål.elementAtOrNull(spørsmålIdx)
        }

    fun nesteSpørsmålITema(temaId: Int, spørsmålId: String) =
        temaer.firstOrNull { it.id == temaId }?.let { tema ->
            val spørsmålIdx = tema.spørsmål.indexOfFirst { it.id == UUID.fromString(spørsmålId) }
            tema.spørsmål.elementAtOrNull(spørsmålIdx + 1)
        }
}
