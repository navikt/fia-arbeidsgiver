package no.nav.fia.arbeidsgiver.sporreundersokelse.domene

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.dto.SpørreundersøkelseSvarDTO

class StengTema(
	spørreundersøkelseId: String,
	temaId: Int,
) : SpørreundersøkelseHendelse<Int>(
    spørreundersøkelseId = spørreundersøkelseId,
    hendelsesType = HendelsType.STENG_TEMA,
    data = temaId
)

class SvarPåSpørsmål(
	spørreundersøkelseId: String,
	svarPåSpørsmål: SpørreundersøkelseSvarDTO,
) : SpørreundersøkelseHendelse<SpørreundersøkelseSvarDTO>(
    spørreundersøkelseId = spørreundersøkelseId,
    hendelsesType = HendelsType.SVAR_PÅ_SPØRSMÅL,
    data = svarPåSpørsmål
)

@Serializable
sealed class SpørreundersøkelseHendelse<T>(
	val spørreundersøkelseId: String,
	val hendelsesType: HendelsType,
	val data: T,
) {
    fun tilNøkkel() =
        Json.encodeToString(SpørreundersøkelseHendelseNøkkel(spørreundersøkelseId, hendelsesType))

    fun tilMelding() =
        when (this) {
            is StengTema -> Json.encodeToString(data)
            is SvarPåSpørsmål -> Json.encodeToString(data)
        }
}

@Serializable
data class SpørreundersøkelseHendelseNøkkel(
	val spørreundersøkelseId: String,
	val hendelsesType: HendelsType,
)

enum class HendelsType {
    STENG_TEMA,
    SVAR_PÅ_SPØRSMÅL
}