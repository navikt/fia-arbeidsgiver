package no.nav.fia.arbeidsgiver.sporreundersokelse.domene

import io.ktor.http.HttpStatusCode
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import no.nav.fia.arbeidsgiver.api.Feil
import no.nav.fia.arbeidsgiver.redis.RedisService
import no.nav.fia.arbeidsgiver.redis.Type
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.KategoristatusDTO
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

class SpørreundersøkelseService(val redisService: RedisService) {
	private val logger: Logger = LoggerFactory.getLogger(this::class.java)

	fun lagre(spørreundersøkelse: Spørreundersøkelse) {
		redisService.lagre(
			Type.SPØRREUNDERSØKELSE,
			spørreundersøkelse.spørreundersøkelseId.toString(),
			Json.encodeToString(spørreundersøkelse)
		)
	}

	fun lagreSesjon(sesjonsId: UUID, spørreundersøkelseId: UUID) {
		redisService.lagre(Type.SESJON, sesjonsId.toString(), spørreundersøkelseId.toString())
	}

	fun lagreAntallDeltakere(spørreundersøkelseId: UUID, antallDeltakere: Int) {
		redisService.lagre(Type.ANTALL_DELTAKERE, spørreundersøkelseId.toString(), antallDeltakere.toString())
	}

	fun lagreKategoristatus(spørreundersøkelseId: UUID, kategoristatus: KategoristatusDTO) {
		redisService.lagre(Type.KATEGORISTATUS, "${kategoristatus.kategori}-$spørreundersøkelseId", Json.encodeToString(kategoristatus))
	}

	fun henteSpørreundersøkelse(spørreundersøkelseId: UUID): Spørreundersøkelse {
		val undersøkelse = redisService.hente(Type.SPØRREUNDERSØKELSE, spørreundersøkelseId.toString())?.let {
			Json.decodeFromString<Spørreundersøkelse>(it)
		} ?: throw Feil(
			feilmelding = "Ukjent spørreundersøkelse '$spørreundersøkelseId'",
			feilkode = HttpStatusCode.Forbidden
		)

		logger.info("Hentet spørreundersøkelse med id '${undersøkelse.spørreundersøkelseId}' og status '${undersøkelse.status}'")
		return undersøkelse
	}

	fun hentePågåendeSpørreundersøkelse(spørreundersøkelseId: UUID): Spørreundersøkelse {
		val undersøkelse = henteSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId)
		return if (undersøkelse.status == SpørreundersøkelseStatus.PÅBEGYNT) {
			undersøkelse
		} else throw Feil(
			feilmelding = "Spørreundersøkelse med id '$spørreundersøkelseId'/'${undersøkelse.spørreundersøkelseId}' har feil status '${undersøkelse.status}'",
			feilkode = HttpStatusCode.Forbidden
		)
	}

	fun henteSpørreundersøkelseIdFraSesjon(sesjonsId: UUID): UUID? {
		return redisService.hente(Type.SESJON, sesjonsId.toString())?.let {
			UUID.fromString(it)
		}
	}

	fun hentAntallDeltakere(spørreundersøkelseId: UUID): Int {
		return redisService.hente(Type.ANTALL_DELTAKERE, spørreundersøkelseId.toString())?.toInt() ?: 0
	}

	fun hentKategoristatus(spørreundersøkelseId: UUID, kategori: Kategori): KategoristatusDTO? {
		val kategoristatusDTO = redisService.hente(
			Type.KATEGORISTATUS,
			"$kategori-$spørreundersøkelseId"
		)?.let { Json.decodeFromString<KategoristatusDTO>(it) }

		if (kategoristatusDTO?.antallSpørsmål != null) {
			return kategoristatusDTO
		} else {
			val spørreundersøkelse = henteSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId)
			val antallSpørsmålIKategori =
				spørreundersøkelse.spørsmålOgSvaralternativer.filter { it.kategori == kategori }.size
			return kategoristatusDTO?.copy(antallSpørsmål = antallSpørsmålIKategori)
		}
	}
}