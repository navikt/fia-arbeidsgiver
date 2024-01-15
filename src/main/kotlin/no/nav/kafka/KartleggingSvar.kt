package no.nav.kafka

import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString


data class KartleggingSvar(
    val spørsmålId: String,
    val spørreundersøkelseId: String,
    val svarId: String,
    val sesjonId: String,
) {
    fun tilNøkkel() = "${sesjonId}_$spørsmålId"


    fun tilMelding(): String {
        return Json.encodeToString(this)
    }
}