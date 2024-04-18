package no.nav.fia.arbeidsgiver.sporreundersokelse.domene

import io.ktor.http.HttpStatusCode
import java.util.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import no.nav.fia.arbeidsgiver.http.Feil
import no.nav.fia.arbeidsgiver.konfigurasjon.KafkaConfig
import no.nav.fia.arbeidsgiver.redis.RedisService
import no.nav.fia.arbeidsgiver.redis.Type
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.vert.dto.TemaStatus
import no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.SpørreundersøkelseHendelseProdusent
import no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.SpørreundersøkelseSvarProdusent
import no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.dto.SpørreundersøkelseAntallSvarDto
import no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.dto.SpørreundersøkelseSvarDTO
import no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.dto.TemaMedSpørsmålOgSvar
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class SpørreundersøkelseService(
    private val redisService: RedisService,
) {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)
    private val spørreundersøkelseSvarProdusent by lazy {
        SpørreundersøkelseSvarProdusent(kafkaConfig = KafkaConfig())
    }
    private val spørreundersøkelseHendelseProdusent by lazy {
        SpørreundersøkelseHendelseProdusent(kafkaConfig = KafkaConfig())
    }

    fun lagre(spørreundersøkelse: Spørreundersøkelse) {
        redisService.lagre(
            type = Type.SPØRREUNDERSØKELSE,
            nøkkel = spørreundersøkelse.spørreundersøkelseId.toString(),
            verdi = Json.encodeToString(spørreundersøkelse)
        )
    }

    fun lagre(spørreundersøkelseAntallSvarDto: SpørreundersøkelseAntallSvarDto) {
        redisService.lagre(
            type = Type.ANTALL_SVAR_FOR_SPØRSMÅL,
            nøkkel = "${spørreundersøkelseAntallSvarDto.spørreundersøkelseId}-${spørreundersøkelseAntallSvarDto.spørsmålId}",
            verdi = spørreundersøkelseAntallSvarDto.antallSvar.toString()
        )
    }

    fun lagre(spørreundersøkelseId: String, spørreundersøkelseResultat: TemaMedSpørsmålOgSvar) {
        redisService.lagre(
            type = Type.SPØRREUNDERSØKELSE_RESULTAT,
            nøkkel = "${spørreundersøkelseId}-${spørreundersøkelseResultat.temaId}",
            verdi = Json.encodeToString(spørreundersøkelseResultat)
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
    }

    fun lagreSesjon(sesjonsId: UUID, spørreundersøkelseId: UUID) {
        redisService.lagre(Type.SESJON, sesjonsId.toString(), spørreundersøkelseId.toString())
    }

    fun lagreAntallDeltakere(spørreundersøkelseId: UUID, antallDeltakere: Int) {
        redisService.lagre(Type.ANTALL_DELTAKERE, spørreundersøkelseId.toString(), antallDeltakere.toString())
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


    fun hentAntallSvar(spørreundersøkelseId: UUID, spørsmålId: UUID): Int {
        return redisService.hente(Type.ANTALL_SVAR_FOR_SPØRSMÅL, "$spørreundersøkelseId-$spørsmålId")?.toInt() ?: 0
    }

    fun hentResultater(spørreundersøkelseId: UUID, temaId: Int): TemaMedSpørsmålOgSvar {
        val resultater =
            redisService.hente(Type.SPØRREUNDERSØKELSE_RESULTAT, "${spørreundersøkelseId}-${temaId}")?.let {
                Json.decodeFromString<TemaMedSpørsmålOgSvar>(it)
            } ?: throw Feil(
                feilmelding = "Ingen resultater for tema '$temaId' i spørreundersøkelse '$spørreundersøkelseId'",
                feilkode = HttpStatusCode.Forbidden
            )
        logger.info("Hentet resultater for tema med id '$temaId med id '$spørreundersøkelseId'")
        return resultater
    }

    fun åpneSpørsmål(spørreundersøkelseId: UUID, spørsmålId: UUID) {
        redisService.lagre(Type.ER_SPØRSMÅL_ÅPENT, "$spørreundersøkelseId-$spørsmålId", "ja")
    }

    fun erSpørsmålÅpent(spørreundersøkelseId: UUID, spørsmålId: UUID): Boolean {
        return redisService.hente(Type.ER_SPØRSMÅL_ÅPENT, "$spørreundersøkelseId-$spørsmålId") != null
    }

    fun sendSvar(
        spørreundersøkelseId: UUID,
        sesjonsId: UUID,
        spørsmålId: UUID,
        svarIder: List<UUID>,
    ) =
        spørreundersøkelseSvarProdusent.sendSvar(
            svar = SpørreundersøkelseSvarDTO(
                spørreundersøkelseId = spørreundersøkelseId.toString(),
                sesjonId = sesjonsId.toString(),
                spørsmålId = spørsmålId.toString(),
                svarIder = svarIder.map { it.toString() },
            )
        )

    fun lukkTema(spørreundersøkelseId: UUID, temaId: Int) {
        spørreundersøkelseHendelseProdusent.sendHendelse(
            hendelse = StengTema(spørreundersøkelseId = spørreundersøkelseId.toString(), temaId = temaId)
        )
        redisService.lagre(Type.TEMA_STATUS, nøkkel = "$spørreundersøkelseId-$temaId", TemaStatus.STENGT.name)
    }

    fun hentTemaStatus(spørreundersøkelseId: UUID, temaId: Int): TemaStatus? {
        return redisService.hente(Type.TEMA_STATUS, nøkkel = "$spørreundersøkelseId-$temaId")?.let {
            Json.decodeFromString(it)
        }
    }
}
