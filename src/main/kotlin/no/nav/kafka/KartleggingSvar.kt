package no.nav.kafka

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

@Serializable
data class KartleggingSvar(
    val spørreundersøkelseId: String,
    val sesjonId: String,
    val spørsmålId: String,
    val svarId: String,
) {
    fun tilNøkkel() = "${sesjonId}_$spørsmålId"

    fun tilMelding(): String {
        return Json.encodeToString(this)
    }
}