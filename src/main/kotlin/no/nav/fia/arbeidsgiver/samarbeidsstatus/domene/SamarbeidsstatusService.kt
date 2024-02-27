package no.nav.fia.arbeidsgiver.samarbeidsstatus.domene

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import no.nav.fia.arbeidsgiver.redis.RedisService
import no.nav.fia.arbeidsgiver.redis.Type

class SamarbeidsstatusService(val redisService: RedisService) {
	fun lagre(iaSakStatus: IASakStatus) {
		val gammelStatus = henteSakStatus(iaSakStatus.orgnr)
		if (gammelStatus == null || gammelStatus.sistOppdatert <= iaSakStatus.sistOppdatert)
			redisService.lagre(Type.SAMARBEIDSSTATUS, iaSakStatus.orgnr, Json.encodeToString(iaSakStatus))
	}


	fun henteSakStatus(orgnr: String): IASakStatus? {
		return redisService.hente(Type.SAMARBEIDSSTATUS, orgnr)?.let {
			Json.decodeFromString(it)
		}
	}
}