package no.nav.fia.arbeidsgiver.samarbeidsstatus.domene

import kotlinx.serialization.json.Json
import no.nav.fia.arbeidsgiver.valkey.Type
import no.nav.fia.arbeidsgiver.valkey.ValkeyService

class SamarbeidsstatusService(
    val valkeyService: ValkeyService,
) {
    companion object {
        const val TO_ÅR = 2 * 365 * 24 * 60 * 60L
    }

    fun lagre(iaSakStatus: IASakStatus) {
        val gammelStatus = henteSakStatus(iaSakStatus.orgnr)
        if (gammelStatus == null || gammelStatus.sistOppdatert <= iaSakStatus.sistOppdatert) {
            valkeyService.lagre(
                type = Type.SAMARBEIDSSTATUS,
                nøkkel = iaSakStatus.orgnr,
                verdi = Json.encodeToString(iaSakStatus),
                ttl = TO_ÅR,
            )
        }
    }

    fun henteSakStatus(orgnr: String): IASakStatus? =
        valkeyService.hente(Type.SAMARBEIDSSTATUS, orgnr)?.let {
            Json.decodeFromString(it)
        }
}
