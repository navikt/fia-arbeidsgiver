package no.nav.fia.arbeidsgiver.sporreundersokelse.domene

import ia.felles.integrasjoner.kafkameldinger.SpørreundersøkelseStatus
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.IdentifiserbartSpørsmål
import no.nav.fia.arbeidsgiver.sporreundersokelse.util.UUIDSerializer
import java.util.*

@Serializable
data class Spørreundersøkelse(
    @Serializable(with = UUIDSerializer::class)
    val spørreundersøkelseId: UUID,
    @Serializable(with = UUIDSerializer::class)
    val vertId: UUID,
    val orgnummer: String,
    val virksomhetsNavn: String,
    val temaMedSpørsmålOgSvaralternativer: List<TemaMedSpørsmålOgSvaralternativer>,
    val status: SpørreundersøkelseStatus,
    val type: String,
    val avslutningsdato: LocalDate,
) {

    fun hentNesteSpørsmålOgTema(nåværendeSpørmålId: UUID): IdentifiserbartSpørsmål? {
        val gjeldendeTema = temaMedSpørsmålOgSvaralternativer.temaFraSpørsmålId(nåværendeSpørmålId)
        val gjeldeneTemaIdx = temaMedSpørsmålOgSvaralternativer.indexOfFirst { it.temaId == gjeldendeTema.temaId }
        val nesteSpørsmålIgjeldeneTema = gjeldendeTema.hentNesteSpørsmål(spørsmålId = nåværendeSpørmålId)
        return if (nesteSpørsmålIgjeldeneTema != null) {
            IdentifiserbartSpørsmål(
                temaId = gjeldendeTema.temaId,
                spørsmålId = nesteSpørsmålIgjeldeneTema.id.toString(),
            )
        } else {
            temaMedSpørsmålOgSvaralternativer.elementAtOrNull(gjeldeneTemaIdx + 1)?.let {
                IdentifiserbartSpørsmål(
                    temaId = it.temaId,
                    spørsmålId = it.spørsmålOgSvaralternativer.first().id.toString()
                )
            }
        }
    }

    fun hentForrigeSpørsmålOgTema(nåværendeSpørmålId: UUID): IdentifiserbartSpørsmål? {
        val gjeldendeTema = temaMedSpørsmålOgSvaralternativer.temaFraSpørsmålId(nåværendeSpørmålId)
        val gjeldeneTemaIdx = temaMedSpørsmålOgSvaralternativer.indexOfFirst { it.temaId == gjeldendeTema.temaId }
        val forrigeSpørsmålIgjeldeneTema = gjeldendeTema.hentForrigeSpørsmål(spørsmålId = nåværendeSpørmålId)
        return if (forrigeSpørsmålIgjeldeneTema != null) {
            IdentifiserbartSpørsmål(
                temaId = gjeldendeTema.temaId,
                spørsmålId = forrigeSpørsmålIgjeldeneTema.id.toString(),
            )
        } else {
            temaMedSpørsmålOgSvaralternativer.elementAtOrNull(gjeldeneTemaIdx - 1)?.let {
                IdentifiserbartSpørsmål(
                    temaId = it.temaId,
                    spørsmålId = it.spørsmålOgSvaralternativer.last().id.toString()
                )
            }
        }
    }
}
