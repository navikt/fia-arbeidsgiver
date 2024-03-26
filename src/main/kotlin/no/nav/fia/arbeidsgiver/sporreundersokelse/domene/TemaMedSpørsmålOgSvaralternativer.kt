package no.nav.fia.arbeidsgiver.sporreundersokelse.domene

import io.ktor.http.HttpStatusCode
import java.util.*
import kotlinx.serialization.Serializable
import no.nav.fia.arbeidsgiver.http.Feil

@Serializable
data class TemaMedSpørsmålOgSvaralternativer(
    val temaId: Int,
    val temanavn: Temanavn,
    val beskrivelse: String,
    val introtekst: String,
    val spørsmålOgSvaralternativer: List<SpørsmålOgSvaralternativer>,
) {
    fun indeksFraSpørsmålId(spørsmålId: UUID): Int {
        val indeks = spørsmålOgSvaralternativer.indexOfFirst { it.id == spørsmålId }
        if (indeks == -1) {
            throw Feil(feilmelding = "Spørsmål med id $spørsmålId ble ikke funnet", feilkode = HttpStatusCode.NotFound)
        }
        return indeks
    }

    fun hentNesteSpørsmål(spørsmålId: UUID): SpørsmålOgSvaralternativer? {
        val gjeldeneSpørsmålIdx = indeksFraSpørsmålId(spørsmålId = spørsmålId)
        return spørsmålOgSvaralternativer.elementAtOrNull(gjeldeneSpørsmålIdx + 1)
    }

    fun hentForrigeSpørsmål(spørsmålId: UUID): SpørsmålOgSvaralternativer? {
        val gjeldeneSpørsmålIdx = indeksFraSpørsmålId(spørsmålId = spørsmålId)
        return spørsmålOgSvaralternativer.elementAtOrNull(gjeldeneSpørsmålIdx - 1)
    }
}

fun List<TemaMedSpørsmålOgSvaralternativer>.spørsmålFraId(spørsmålId: UUID) =
    this.flatMap { it.spørsmålOgSvaralternativer }.firstOrNull { it.id == spørsmålId }
        ?: throw Feil("Fant ikke spørsmål $spørsmålId i spørreundersøkelse", feilkode = HttpStatusCode.NotFound)

fun List<TemaMedSpørsmålOgSvaralternativer>.temaFraSpørsmålId(spørsmålId: UUID) =
    this.find { tema ->
        tema.spørsmålOgSvaralternativer.any { spørsmål ->
            spørsmål.id == spørsmålId
        }
    } ?: throw Feil("Fant ikke tema til spørsmålId $spørsmålId", feilkode = HttpStatusCode.NotFound)

