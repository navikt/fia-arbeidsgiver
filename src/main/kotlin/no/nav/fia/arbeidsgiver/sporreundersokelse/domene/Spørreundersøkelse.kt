package no.nav.fia.arbeidsgiver.sporreundersokelse.domene

import ia.felles.integrasjoner.kafkameldinger.SpørreundersøkelseStatus
import java.util.UUID
import kotlinx.datetime.LocalDate
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.IdentifiserbartSpørsmål

data class Spørreundersøkelse(
    val id: UUID,
    val vertId: UUID?,
    val orgnummer: String,
    val virksomhetsNavn: String,
    val status: SpørreundersøkelseStatus,
    val type: String,
    val avslutningsdato: LocalDate,
    val temaer: List<Tema>,
) {
    fun hentNesteSpørsmålOgTema(nåværendeSpørmålId: UUID): IdentifiserbartSpørsmål? {
        val gjeldendeTema = temaer.temaFraSpørsmålId(nåværendeSpørmålId)
        val gjeldeneTemaIdx = temaer.indexOfFirst { it.id == gjeldendeTema.id }
        val nesteSpørsmålIgjeldeneTema = gjeldendeTema.hentNesteSpørsmål(spørsmålId = nåværendeSpørmålId)
        return if (nesteSpørsmålIgjeldeneTema != null) {
            IdentifiserbartSpørsmål(
                temaId = gjeldendeTema.id,
                spørsmålId = nesteSpørsmålIgjeldeneTema.id.toString(),
            )
        } else {
            temaer.elementAtOrNull(gjeldeneTemaIdx + 1)?.let {
                IdentifiserbartSpørsmål(
                    temaId = it.id,
                    spørsmålId = it.spørsmål.first().id.toString()
                )
            }
        }
    }

    fun hentForrigeSpørsmålOgTema(nåværendeSpørmålId: UUID): IdentifiserbartSpørsmål? {
        val gjeldendeTema = temaer.temaFraSpørsmålId(nåværendeSpørmålId)
        val gjeldeneTemaIdx = temaer.indexOfFirst { it.id == gjeldendeTema.id }
        val forrigeSpørsmålIgjeldeneTema = gjeldendeTema.hentForrigeSpørsmål(spørsmålId = nåværendeSpørmålId)
        return if (forrigeSpørsmålIgjeldeneTema != null) {
            IdentifiserbartSpørsmål(
                temaId = gjeldendeTema.id,
                spørsmålId = forrigeSpørsmålIgjeldeneTema.id.toString(),
            )
        } else {
            temaer.elementAtOrNull(gjeldeneTemaIdx - 1)?.let {
                IdentifiserbartSpørsmål(
                    temaId = it.id,
                    spørsmålId = it.spørsmål.last().id.toString()
                )
            }
        }
    }

    fun hentSpørsmålITema(spørsmål: IdentifiserbartSpørsmål) =
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
