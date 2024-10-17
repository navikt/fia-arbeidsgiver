package no.nav.fia.arbeidsgiver.samarbeidsstatus.domene

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import no.nav.fia.arbeidsgiver.redis.RedisService
import no.nav.fia.arbeidsgiver.redis.Type

class SamarbeidsstatusService(
    val redisService: RedisService,
) {
    companion object {
        const val TO_ÅR = 2 * 365 * 24 * 60 * 60L
    }

    fun lagre(iaSakStatus: IASakStatus) {
        val gammelStatus = henteSakStatus(iaSakStatus.orgnr)
        if (gammelStatus == null || gammelStatus.sistOppdatert <= iaSakStatus.sistOppdatert) {
            redisService.lagre(
                type = Type.SAMARBEIDSSTATUS,
                nøkkel = iaSakStatus.orgnr,
                verdi = Json.encodeToString(iaSakStatus),
                ttl = TO_ÅR,
            )
        }
    }

    fun henteSakStatus(orgnr: String): IASakStatus? =
        redisService.hente(Type.SAMARBEIDSSTATUS, orgnr)?.let {
            Json.decodeFromString(it)
        }
}
