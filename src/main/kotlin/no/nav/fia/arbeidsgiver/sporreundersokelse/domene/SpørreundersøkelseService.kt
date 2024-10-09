package no.nav.fia.arbeidsgiver.sporreundersokelse.domene

import ia.felles.integrasjoner.kafkameldinger.SpørreundersøkelseStatus
import io.ktor.http.HttpStatusCode
import java.util.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import no.nav.fia.arbeidsgiver.http.Feil
import no.nav.fia.arbeidsgiver.konfigurasjon.KafkaConfig
import no.nav.fia.arbeidsgiver.redis.RedisService
import no.nav.fia.arbeidsgiver.redis.Type
import no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.SpørreundersøkelseHendelseProdusent
import no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.SpørreundersøkelseHendelseProdusent.StengTema
import no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.SpørreundersøkelseKonsument.SerializableSpørreundersøkelse
import no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.SpørreundersøkelseOppdateringKonsument.SpørreundersøkelseAntallSvarDto
import no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.SpørreundersøkelseOppdateringKonsument.TemaResultatDto
import no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.SpørreundersøkelseSvarProdusent
import no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.SpørreundersøkelseSvarProdusent.SpørreundersøkelseSvarDTO
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

    fun lagre(spørreundersøkelse: SerializableSpørreundersøkelse) {
        redisService.lagre(
            type = Type.SPØRREUNDERSØKELSE,
            nøkkel = spørreundersøkelse.spørreundersøkelseId,
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

    fun lagre(spørreundersøkelseId: String, temaresultat: TemaResultatDto) {
        redisService.lagre(
            type = Type.SPØRREUNDERSØKELSE_RESULTAT,
            nøkkel = "${spørreundersøkelseId}-${temaresultat.temaId}",
            verdi = Json.encodeToString(temaresultat)
        )
    }

    fun slett(spørreundersøkelse: SerializableSpørreundersøkelse) {
        logger.info("Sletter spørreundersøkelse med id: '${spørreundersøkelse.spørreundersøkelseId}'")
        listOf(Type.SPØRREUNDERSØKELSE, Type.ANTALL_DELTAKERE).forEach {
            logger.info("Sletter type '$it' for spørreundersøkelse med id: '${spørreundersøkelse.spørreundersøkelseId}'")
            redisService.slett(it, spørreundersøkelse.spørreundersøkelseId)
        }
    }

    fun lagreSesjon(sesjonsId: UUID, spørreundersøkelseId: UUID) {
        redisService.lagre(Type.SESJON, sesjonsId.toString(), spørreundersøkelseId.toString())
    }

    fun lagreAntallDeltakere(spørreundersøkelseId: UUID, antallDeltakere: Int) {
        redisService.lagre(Type.ANTALL_DELTAKERE, spørreundersøkelseId.toString(), antallDeltakere.toString())
    }

    fun henteSpørreundersøkelse(spørreundersøkelseId: UUID): SerializableSpørreundersøkelse {
        val undersøkelse = redisService.hente(Type.SPØRREUNDERSØKELSE, spørreundersøkelseId.toString())?.let {
            Json.decodeFromString<SerializableSpørreundersøkelse>(it)
        } ?: throw Feil(
            feilmelding = "Ukjent spørreundersøkelse '$spørreundersøkelseId'",
            feilkode = HttpStatusCode.Forbidden
        )

        logger.info("Hentet spørreundersøkelse med id '${undersøkelse.spørreundersøkelseId}' og status '${undersøkelse.status}'")
        return undersøkelse
    }

    private fun SpørreundersøkelseStatus.kanVisesForVert() = when (this) {
        SpørreundersøkelseStatus.PÅBEGYNT, SpørreundersøkelseStatus.AVSLUTTET -> true
        else -> false
    }

    fun hentSpørreundersøkelseSomVert(spørreundersøkelseId: UUID): Spørreundersøkelse {
        val undersøkelse = henteSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId)
        return if (undersøkelse.status.kanVisesForVert()) {
            undersøkelse.tilDomene()
        } else throw Feil(
            feilmelding = "Spørreundersøkelse med id '$spørreundersøkelseId' har feil status '${undersøkelse.status}'",
            feilkode = HttpStatusCode.Forbidden
        )
    }

    fun hentePågåendeSpørreundersøkelse(spørreundersøkelseId: UUID): Spørreundersøkelse {
        val undersøkelse = henteSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId)
        return if (undersøkelse.status == SpørreundersøkelseStatus.PÅBEGYNT) {
            undersøkelse.tilDomene()
        } else throw Feil(
            feilmelding = "Spørreundersøkelse med id '$spørreundersøkelseId' har feil status '${undersøkelse.status}'",
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

    fun hentResultater(spørreundersøkelseId: UUID, temaId: Int): TemaResultatDto {
        val resultater =
            redisService.hente(Type.SPØRREUNDERSØKELSE_RESULTAT, "${spørreundersøkelseId}-${temaId}")?.let {
                Json.decodeFromString<TemaResultatDto>(it)
            } ?: throw Feil(
                feilmelding = "Ingen resultater for tema '$temaId' i spørreundersøkelse '$spørreundersøkelseId'",
                feilkode = HttpStatusCode.Forbidden
            )
        logger.info("Hentet resultater for tema med id '$temaId med id '$spørreundersøkelseId'")
        return resultater
    }

    fun åpneTema(spørreundersøkelseId: UUID, temaId: Int) {
        redisService.lagre(Type.ER_TEMA_ÅPENT, "$spørreundersøkelseId-$temaId", "ja")
    }

    fun erTemaÅpent(spørreundersøkelseId: UUID, temaId: Int) =
        redisService.hente(Type.ER_TEMA_ÅPENT, "$spørreundersøkelseId-$temaId") != null

    fun erSpørsmålÅpent(spørreundersøkelseId: UUID, temaId: Int, spørsmålId: UUID): Boolean {
        return redisService.hente(Type.ER_SPØRSMÅL_ÅPENT, "$spørreundersøkelseId-$spørsmålId") != null ||
                erTemaÅpent(spørreundersøkelseId = spørreundersøkelseId, temaId = temaId)
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
            hendelse = StengTema(
                spørreundersøkelseId = spørreundersøkelseId.toString(),
                temaId = temaId
            )
        )
        redisService.lagre(Type.TEMA_STATUS, nøkkel = "$spørreundersøkelseId-$temaId", TemaStatus.STENGT.name)
    }

    fun erTemaStengt(spørreundersøkelseId: UUID, temaId: Int) =
        hentTemaStatus(spørreundersøkelseId = spørreundersøkelseId, temaId = temaId) == TemaStatus.STENGT


    private fun hentTemaStatus(spørreundersøkelseId: UUID, temaId: Int): TemaStatus? {
        return redisService.hente(Type.TEMA_STATUS, nøkkel = "$spørreundersøkelseId-$temaId")?.let {
            TemaStatus.valueOf(it)
        }
    }

    fun erAlleTemaerErStengt(spørreundersøkelse: Spørreundersøkelse): Boolean {
        return spørreundersøkelse.temaer.all { tema ->
            TemaStatus.STENGT == hentTemaStatus(spørreundersøkelse.id, tema.id)
        }
    }

    fun antallSvarPåSpørsmålMedFærrestBesvarelser(
        tema: Tema,
        spørreundersøkelse: Spørreundersøkelse,
    ): Int {
        val antallSvarPerSpørsmål = tema.spørsmål.map { spørsmål ->
            hentAntallSvar(
                spørreundersøkelseId = spørreundersøkelse.id,
                spørsmålId = spørsmål.id
            )
        }

        val antallSvarPåSpørsmålMedFærrestBesvarelser = antallSvarPerSpørsmål.min()
        return antallSvarPåSpørsmålMedFærrestBesvarelser
    }
}
