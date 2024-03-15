package no.nav.fia.arbeidsgiver.sporreundersokelse.domene

import io.ktor.http.HttpStatusCode
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import no.nav.fia.arbeidsgiver.http.Feil
import no.nav.fia.arbeidsgiver.konfigurasjon.KafkaConfig
import no.nav.fia.arbeidsgiver.redis.RedisService
import no.nav.fia.arbeidsgiver.redis.Type
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.TemastatusDTO
import no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.dto.SpørreundersøkelseAntallSvarDto
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.NesteSpørsmålDTO
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.tilUUID
import no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.SpørreundersøkelseSvarProdusent
import no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.dto.SpørreundersøkelseSvarDTO

class SpørreundersøkelseService(
    val redisService: RedisService,
) {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)
    private val spørreundersøkelseSvarProdusent by lazy {
        SpørreundersøkelseSvarProdusent(kafkaConfig = KafkaConfig())
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

    fun hentNesteSpørsmål(
        spørreundersøkelseId: UUID,
        nåværendeSpørsmålId: String,
        tema: Tema,
    ): NesteSpørsmålDTO {
        val spørreundersøkelse = hentePågåendeSpørreundersøkelse(spørreundersøkelseId)

        val temastatus = hentTemastatus(spørreundersøkelseId = spørreundersøkelseId, tema = tema)
            ?: throw Feil(
                "Finner ikke temastatus på spørreundersøkelse $spørreundersøkelseId",
                feilkode = HttpStatusCode.InternalServerError
            )

        val gjeldendeSpørsmålIndeks = if (nåværendeSpørsmålId.uppercase() == "START") {
            -1
        } else {
            spørreundersøkelse.indeksFraSpørsmålId(tema, nåværendeSpørsmålId.tilUUID("spørsmålId"))
        }


        val nesteSpørsmålId: String? =
            if (gjeldendeSpørsmålIndeks + 1 < spørreundersøkelse.spørsmålOgSvaralternativer.size) {
                spørreundersøkelse.spørsmålOgSvaralternativer[gjeldendeSpørsmålIndeks + 1].id.toString()
            } else null

        val forrigeSpørsmålId: String? =
            if (gjeldendeSpørsmålIndeks - 1 >= 0) {
                spørreundersøkelse.spørsmålOgSvaralternativer[gjeldendeSpørsmålIndeks - 1].id.toString()
            } else null

        return NesteSpørsmålDTO(
            hvaErNesteSteg = if ( gjeldendeSpørsmålIndeks >= temastatus.antallSpørsmål - 1) {
                "FERDIG"
            } else {
                "NYTT_SPØRSMÅL"
            },
            erNesteÅpnetAvVert = gjeldendeSpørsmålIndeks + 1 <= temastatus.spørsmålindeks,
            nesteSpørsmålId = nesteSpørsmålId,
            forrigeSpørsmålId = forrigeSpørsmålId,
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
        return redisService.hente(
            Type.TEMASTATUS,
            "$tema-$spørreundersøkelseId"
        )?.let { Json.decodeFromString<TemastatusDTO>(it) }
    }

    fun hentAntallSvar(spørreundersøkelseId: UUID, spørsmålId: UUID): Int {
        return redisService.hente(Type.ANTALL_SVAR_FOR_SPØRSMÅL, "$spørreundersøkelseId-$spørsmålId")?.toInt() ?: 0
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
        svarId: UUID,
    )  =
        spørreundersøkelseSvarProdusent.sendSvar(svar = SpørreundersøkelseSvarDTO(
            spørreundersøkelseId = spørreundersøkelseId.toString(),
            sesjonId = sesjonsId.toString(),
            spørsmålId = spørsmålId.toString(),
            svarId = svarId.toString(),
        ))
}