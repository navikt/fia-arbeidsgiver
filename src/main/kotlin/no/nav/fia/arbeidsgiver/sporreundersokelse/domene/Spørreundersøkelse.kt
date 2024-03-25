package no.nav.fia.arbeidsgiver.sporreundersokelse.domene

import io.ktor.http.HttpStatusCode
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import java.util.UUID
import no.nav.fia.arbeidsgiver.http.Feil
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

    fun indeksFraSpørsmålId(tema: Tema, spørsmålId: UUID): Int {
        val indeks = hentAlleSpørsmålITema(tema = tema).indexOfFirst { it.id == spørsmålId }
        if (indeks == -1) {
            throw Feil(feilmelding = "Spørsmål med id $spørsmålId ble ikke funnet", feilkode = HttpStatusCode.NotFound)
        }
        return indeks
    }

    fun hentNesteSpørsmål(tema: Tema, spørsmålId: UUID): SpørsmålOgSvaralternativer? {
        val gjeldeneSpørsmålIdx = indeksFraSpørsmålId(tema = tema, spørsmålId = spørsmålId)
        return hentAlleSpørsmålITema(tema = tema).elementAtOrNull(gjeldeneSpørsmålIdx + 1)
    }

    fun hentForrigeSpørsmål(tema: Tema, spørsmålId: UUID): SpørsmålOgSvaralternativer? {
        val gjeldeneSpørsmålIdx = indeksFraSpørsmålId(tema = tema, spørsmålId = spørsmålId)
        return hentAlleSpørsmålITema(tema = tema).elementAtOrNull(gjeldeneSpørsmålIdx - 1)
    }

    fun hentNesteSpørsmålOgTema(nåværendeTema: Tema, nåværendeSpørmålId: UUID): IdentifiserbartSpørsmål? {
        val gjeldeneTemaIdx = temaMedSpørsmålOgSvaralternativer.indexOfFirst { it.tema == nåværendeTema }
        val nesteSpørsmålIgjeldeneTema = hentNesteSpørsmål(tema = nåværendeTema, spørsmålId = nåværendeSpørmålId)
        return if (nesteSpørsmålIgjeldeneTema != null) {
            IdentifiserbartSpørsmål(
                tema = nåværendeTema,
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

    fun hentForrigeSpørsmålOgTema(nåværendeTema: Tema, nåværendeSpørmålId: UUID): IdentifiserbartSpørsmål? {
        val gjeldeneTemaIdx = temaMedSpørsmålOgSvaralternativer.indexOfFirst { it.tema == nåværendeTema }
        val forrigeSpørsmålIgjeldeneTema = hentForrigeSpørsmål(tema = nåværendeTema, spørsmålId = nåværendeSpørmålId)
        return if (forrigeSpørsmålIgjeldeneTema != null) {
            IdentifiserbartSpørsmål(
                tema = nåværendeTema,
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

    fun spørsmålFraId(tema: Tema, spørsmålId: UUID): SpørsmålOgSvaralternativer {
        val spørsmålOgSvaralternativer =
            hentAlleSpørsmålITema(tema = tema).firstOrNull { it.id == spørsmålId }

        if (spørsmålOgSvaralternativer == null) {
            throw Feil(feilmelding = "Spørsmål med id $spørsmålId ble ikke funnet", feilkode = HttpStatusCode.NotFound)
        }
        return spørsmålOgSvaralternativer
    }

    fun hentAlleSpørsmålITema(tema: Tema) =
        temaMedSpørsmålOgSvaralternativer.firstOrNull { it.tema == tema }?.spørsmålOgSvaralternativer
            ?: throw Feil("Fant ikke tema $tema", feilkode = HttpStatusCode.NotFound)

}
