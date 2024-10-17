package no.nav.fia.arbeidsgiver.samarbeidsstatus.api.dto

import kotlinx.serialization.Serializable
import no.nav.fia.arbeidsgiver.samarbeidsstatus.domene.IASakStatus

@Serializable
data class SamarbeidsstatusDTO(
    val orgnr: String,
    val samarbeid: Samarbeidsstaus,
)

fun IASakStatus.tilSamarbeid() = SamarbeidsstatusDTO(orgnr, Samarbeidsstaus.fraIASakStatus(status))
