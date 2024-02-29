package no.nav.fia.arbeidsgiver.sporreundersokelse.domene

import io.ktor.http.HttpStatusCode
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import no.nav.fia.arbeidsgiver.http.Feil
import no.nav.fia.arbeidsgiver.redis.RedisService
import no.nav.fia.arbeidsgiver.redis.Type
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.TemastatusDTO
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

	fun slett(spørreundersøkelse: Spørreundersøkelse) {
		logger.info("Sletter spørreundersøkelse med id: '${spørreundersøkelse.spørreundersøkelseId}'")
		// -- slett enkle nøkler basert på spørreundersøkelsens id
		listOf(Type.SPØRREUNDERSØKELSE, Type.ANTALL_DELTAKERE).forEach {
			logger.info("Sletter type '$it' for spørreundersøkelse med id: '${spørreundersøkelse.spørreundersøkelseId}'")
			redisService.slett(it, spørreundersøkelse.spørreundersøkelseId.toString())
		}

		// -- TODO: slett sesjoner knyttet til spørreundersøkelsen

		// -- slett temastatus knyttet til spørreundersøkelsen
		Tema.entries.forEach { tema ->
			logger.info("Sletter type '${Type.TEMASTATUS}', tema '$tema' for spørreundersøkelse med id: '${spørreundersøkelse.spørreundersøkelseId}'")
			redisService.slett(Type.TEMASTATUS, "$tema-${spørreundersøkelse.spørreundersøkelseId}")
		}
	}

	fun lagreSesjon(sesjonsId: UUID, spørreundersøkelseId: UUID) {
		redisService.lagre(Type.SESJON, sesjonsId.toString(), spørreundersøkelseId.toString())
	}

	fun lagreAntallDeltakere(spørreundersøkelseId: UUID, antallDeltakere: Int) {
		redisService.lagre(Type.ANTALL_DELTAKERE, spørreundersøkelseId.toString(), antallDeltakere.toString())
	}

	fun lagreTemastatus(spørreundersøkelseId: UUID, temastatus: TemastatusDTO) {
		redisService.lagre(Type.TEMASTATUS, "${temastatus.tema}-$spørreundersøkelseId", Json.encodeToString(temastatus))
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

	fun hentTemastatus(spørreundersøkelseId: UUID, tema: Tema): TemastatusDTO? {
		val temastatusDTO = redisService.hente(
			Type.TEMASTATUS,
			"$tema-$spørreundersøkelseId"
		)?.let { Json.decodeFromString<TemastatusDTO>(it) }

		if (temastatusDTO?.antallSpørsmål != null) {
			return temastatusDTO
		} else {
			val spørreundersøkelse = henteSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId)
			val antallSpørsmålITema =
				spørreundersøkelse.spørsmålOgSvaralternativer.filter { it.tema == tema }.size
			return temastatusDTO?.copy(antallSpørsmål = antallSpørsmålITema)
		}
	}
}