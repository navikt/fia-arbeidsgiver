package no.nav.fia.arbeidsgiver.sporreundersokelse.domene

import io.ktor.http.HttpStatusCode
import java.util.UUID
import no.nav.fia.arbeidsgiver.http.Feil

data class Tema(
    val id: Int,
    val navn: String,
    val spørsmål: List<Spørsmål>,
) {
    fun indeksFraSpørsmålId(spørsmålId: UUID): Int {
        val indeks = spørsmål.indexOfFirst { it.id == spørsmålId }
        if (indeks == -1) {
            throw Feil(feilmelding = "Spørsmål med id $spørsmålId ble ikke funnet", feilkode = HttpStatusCode.NotFound)
        }
        return indeks
    }

    fun hentNesteSpørsmål(spørsmålId: UUID): Spørsmål? {
        val gjeldeneSpørsmålIdx = indeksFraSpørsmålId(spørsmålId = spørsmålId)
        return spørsmål.elementAtOrNull(gjeldeneSpørsmålIdx + 1)
    }

    fun hentForrigeSpørsmål(spørsmålId: UUID): Spørsmål? {
        val gjeldeneSpørsmålIdx = indeksFraSpørsmålId(spørsmålId = spørsmålId)
        return spørsmål.elementAtOrNull(gjeldeneSpørsmålIdx - 1)
    }
}

fun List<Tema>.spørsmålFraId(spørsmålId: UUID) =
    this.flatMap { it.spørsmål }.firstOrNull { it.id == spørsmålId }
        ?: throw Feil("Fant ikke spørsmål $spørsmålId i spørreundersøkelse", feilkode = HttpStatusCode.NotFound)

fun List<Tema>.temaFraSpørsmålId(spørsmålId: UUID) =
    this.find { tema ->
        tema.spørsmål.any { spørsmål ->
            spørsmål.id == spørsmålId
        }
    } ?: throw Feil("Fant ikke tema til spørsmålId $spørsmålId", feilkode = HttpStatusCode.NotFound)

