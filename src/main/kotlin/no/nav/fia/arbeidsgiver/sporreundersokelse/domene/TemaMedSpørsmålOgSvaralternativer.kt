package no.nav.fia.arbeidsgiver.sporreundersokelse.domene

import io.ktor.http.HttpStatusCode
import java.util.*
import kotlinx.serialization.Serializable
import no.nav.fia.arbeidsgiver.http.Feil

@Serializable
data class TemaMedSpørsmålOgSvaralternativer(
    val tema: Tema,
    val beskrivelse: String,
    val introtekst: String,
    val spørsmålOgSvaralternativer: List<SpørsmålOgSvaralternativer>,
)

fun List<TemaMedSpørsmålOgSvaralternativer>.spørsmålFraId(
    spørsmålId: UUID,
): SpørsmålOgSvaralternativer {

    return this.flatMap { it.spørsmålOgSvaralternativer }.firstOrNull { it.id == spørsmålId }
        ?: throw Feil("Fant ikke spørsmål $spørsmålId i liste av temaer", feilkode = HttpStatusCode.NotFound)
}