package no.nav.fia.arbeidsgiver.persistence

import io.ktor.http.*
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisURI
import io.lettuce.core.StaticCredentialsProvider
import io.lettuce.core.api.sync.RedisCommands
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import no.nav.fia.arbeidsgiver.api.Feil
import no.nav.fia.arbeidsgiver.api.sporreundersokelse.Kategori
import no.nav.fia.arbeidsgiver.api.sporreundersokelse.KategoristatusDTO
import no.nav.fia.arbeidsgiver.domene.samarbeidsstatus.IASakStatus
import no.nav.fia.arbeidsgiver.domene.sporreundersokelse.Spørreundersøkelse
import no.nav.fia.arbeidsgiver.domene.sporreundersokelse.SpørreundersøkelseStatus
import no.nav.fia.arbeidsgiver.konfigurasjon.Redis
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*


class RedisService(
    url: String = Redis.redisUrl,
    username: String = Redis.redisUsername,
    password: String? = Redis.redisPassword
) {
    val redisUri: RedisURI = RedisURI.create(url)
    val sync: RedisCommands<String, String>
    val defaultTimeToLiveSeconds: Long
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    init {
        redisUri.credentialsProvider = StaticCredentialsProvider(username, password?.toCharArray())

        val redisClient = RedisClient.create(redisUri)
        val connection = redisClient.connect()
        sync = connection.sync()

        val TWO_YEARS = 2 * 365 * 24 * 60 * 60L
        defaultTimeToLiveSeconds = TWO_YEARS
    }

    fun lagre(iaSakStatus: IASakStatus) {
        val gammelStatus = henteSakStatus(iaSakStatus.orgnr)
        if (gammelStatus == null || gammelStatus.sistOppdatert <= iaSakStatus.sistOppdatert)
            lagre(Type.SAMARBEIDSSTATUS, iaSakStatus.orgnr, Json.encodeToString(iaSakStatus))
    }

    fun lagre(spørreundersøkelse: Spørreundersøkelse) {
        lagre(
            Type.SPØRREUNDERSØKELSE,
            spørreundersøkelse.spørreundersøkelseId.toString(),
            Json.encodeToString(spørreundersøkelse)
        )
    }

    fun lagreSesjon(sesjonsId: UUID, spørreundersøkelseId: UUID) {
        lagre(Type.SESJON, sesjonsId.toString(), spørreundersøkelseId.toString())
    }

    fun lagreAntallDeltakere(spørreundersøkelseId: UUID, antallDeltakere: Int) {
        lagre(Type.ANTALL_DELTAKERE, spørreundersøkelseId.toString(), antallDeltakere.toString())
    }

    @Deprecated("Skal erstattes av Spørsmålsstatus")
    fun lagreSpørsmålindeks(spørreundersøkelseId: UUID, spørsmålindeks: Int) {
        lagre(Type.SPØRSMÅLINDEKS, spørreundersøkelseId.toString(), spørsmålindeks.toString())
    }

    fun lagreKategoristatus(spørreundersøkelseId: UUID, kategoristatus: KategoristatusDTO) {
        lagre(
            Type.KATEGORISTATUS,
            "${kategoristatus.kategori}-$spørreundersøkelseId",
            Json.encodeToString(kategoristatus)
        )
    }

    fun henteSakStatus(orgnr: String): IASakStatus? {
        return hente(Type.SAMARBEIDSSTATUS, orgnr)?.let {
            Json.decodeFromString(it)
        }
    }

    fun henteSpørreundersøkelse(spørreundersøkelseId: UUID): Spørreundersøkelse {
        val undersøkelse = hente(Type.SPØRREUNDERSØKELSE, spørreundersøkelseId.toString())?.let {
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
        return hente(Type.SESJON, sesjonsId.toString())?.let {
            UUID.fromString(it)
        }
    }

    fun hentAntallDeltakere(spørreundersøkelseId: UUID): Int {
        return hente(Type.ANTALL_DELTAKERE, spørreundersøkelseId.toString())?.toInt() ?: 0
    }

    fun hentKategoristatus(spørreundersøkelseId: UUID, kategori: Kategori): KategoristatusDTO? {
        val kategoristatusDTO = hente(
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

    private fun lagre(
        type: Type,
        nøkkel: String,
        verdi: String,
        ttl: Long = defaultTimeToLiveSeconds,
    ) {
        sync.setex("${type.name}-$nøkkel", ttl, verdi)
    }

    private fun hente(
        type: Type,
        nøkkel: String
    ): String? {
        return sync.get("${type.name}-$nøkkel")
    }
}

enum class Type {
    SAMARBEIDSSTATUS,
    SPØRREUNDERSØKELSE,
    SESJON, ANTALL_DELTAKERE,

    @Deprecated("Skal erstattes av Kategoristatus")
    SPØRSMÅLINDEKS,
    KATEGORISTATUS
}