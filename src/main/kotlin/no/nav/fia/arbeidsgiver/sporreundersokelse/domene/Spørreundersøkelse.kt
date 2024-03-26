package no.nav.fia.arbeidsgiver.sporreundersokelse.domene

import java.util.UUID
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.IdentifiserbartSpørsmål
import no.nav.fia.arbeidsgiver.sporreundersokelse.util.UUIDSerializer

@Serializable
data class Spørreundersøkelse(
    @Serializable(with = UUIDSerializer::class)
    val spørreundersøkelseId: UUID,
    @Serializable(with = UUIDSerializer::class)
    val vertId: UUID,
    val temaMedSpørsmålOgSvaralternativer: List<TemaMedSpørsmålOgSvaralternativer>,
    val status: SpørreundersøkelseStatus,
    val type: String,
    val avslutningsdato: LocalDate,
) {

    fun hentNesteSpørsmålOgTema(nåværendeSpørmålId: UUID): IdentifiserbartSpørsmål? {
        val gjeldendeTema = temaMedSpørsmålOgSvaralternativer.temaFraSpørsmålId(nåværendeSpørmålId)
        val gjeldeneTemaIdx = temaMedSpørsmålOgSvaralternativer.indexOfFirst { it.tema == gjeldendeTema.tema }
        val nesteSpørsmålIgjeldeneTema = gjeldendeTema.hentNesteSpørsmål(spørsmålId = nåværendeSpørmålId)
        return if (nesteSpørsmålIgjeldeneTema != null) {
            IdentifiserbartSpørsmål(
                tema = gjeldendeTema.tema,
                spørsmålId = nesteSpørsmålIgjeldeneTema.id.toString(),
            )
        } else {
            temaMedSpørsmålOgSvaralternativer.elementAtOrNull(gjeldeneTemaIdx + 1)?.let {
                IdentifiserbartSpørsmål(
                    tema = it.tema,
                    spørsmålId = it.spørsmålOgSvaralternativer.first().id.toString()
                )
            }
        }
    }

    fun hentForrigeSpørsmålOgTema(nåværendeSpørmålId: UUID): IdentifiserbartSpørsmål? {
        val gjeldendeTema = temaMedSpørsmålOgSvaralternativer.temaFraSpørsmålId(nåværendeSpørmålId)
        val gjeldeneTemaIdx = temaMedSpørsmålOgSvaralternativer.indexOfFirst { it.tema == gjeldendeTema.tema }
        val forrigeSpørsmålIgjeldeneTema = gjeldendeTema.hentForrigeSpørsmål(spørsmålId = nåværendeSpørmålId)
        return if (forrigeSpørsmålIgjeldeneTema != null) {
            IdentifiserbartSpørsmål(
                tema = gjeldendeTema.tema,
                spørsmålId = forrigeSpørsmålIgjeldeneTema.id.toString(),
            )
        } else {
            temaMedSpørsmålOgSvaralternativer.elementAtOrNull(gjeldeneTemaIdx - 1)?.let {
                IdentifiserbartSpørsmål(
                    tema = it.tema,
                    spørsmålId = it.spørsmålOgSvaralternativer.last().id.toString()
                )
            }
        }
    }
}
