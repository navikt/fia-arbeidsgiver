package no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

@Serializable
data class SpørreundersøkelseSvarDTO(
    val spørreundersøkelseId: String,
    val sesjonId: String,
    val spørsmålId: String,
    val svarId: String,
    val svarIder: List<String>,
) {
    fun tilNøkkel() = "${sesjonId}_$spørsmålId"

    fun tilMelding(): String {
        return Json.encodeToString(this)
    }
}