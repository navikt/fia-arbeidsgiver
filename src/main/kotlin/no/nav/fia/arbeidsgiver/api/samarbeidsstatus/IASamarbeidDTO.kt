package no.nav.fia.arbeidsgiver.api.samarbeidsstatus

import kotlinx.serialization.Serializable
import no.nav.fia.arbeidsgiver.domene.samarbeidsstatus.IASakStatus

enum class Samarbeid {
    IKKE_I_SAMARBEID, I_SAMARBEID;

    companion object {
        fun fraIAStatus(status: String) = when(status) {
            "VI_BISTÅR" -> I_SAMARBEID
            else -> IKKE_I_SAMARBEID
        }
    }
}

@Serializable
data class IASamarbeidDTO(
    val orgnr: String,
    val samarbeid: Samarbeid
)

fun IASakStatus.tilSamarbeid() = IASamarbeidDTO(orgnr, no.nav.fia.arbeidsgiver.api.samarbeidsstatus.Samarbeid.fraIAStatus(status))