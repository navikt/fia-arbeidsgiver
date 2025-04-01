package no.nav.fia.arbeidsgiver.sporreundersokelse.domene

import ia.felles.integrasjoner.kafkameldinger.spørreundersøkelse.SpørreundersøkelseStatus
import ia.felles.integrasjoner.kafkameldinger.spørreundersøkelse.SpørreundersøkelseStatus.AVSLUTTET
import ia.felles.integrasjoner.kafkameldinger.spørreundersøkelse.SpørreundersøkelseStatus.PÅBEGYNT
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import no.nav.fia.arbeidsgiver.http.Feil
import no.nav.fia.arbeidsgiver.konfigurasjon.Kafka
import no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.SpørreundersøkelseHendelseProdusent
import no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.SpørreundersøkelseHendelseProdusent.StengTema
import no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.SpørreundersøkelseKonsument.SerializableSpørreundersøkelse
import no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.SpørreundersøkelseOppdateringKonsument.SpørreundersøkelseAntallSvarDto
import no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.SpørreundersøkelseOppdateringKonsument.TemaResultatDto
import no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.SpørreundersøkelseSvarProdusent
import no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.SpørreundersøkelseSvarProdusent.SpørreundersøkelseSvarDTO
import no.nav.fia.arbeidsgiver.valkey.Type
import no.nav.fia.arbeidsgiver.valkey.ValkeyService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.UUID

class SpørreundersøkelseService(
    private val valkeyService: ValkeyService,
) {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)
    private val spørreundersøkelseSvarProdusent by lazy {
        SpørreundersøkelseSvarProdusent(kafka = Kafka())
    }
    private val spørreundersøkelseHendelseProdusent by lazy {
        SpørreundersøkelseHendelseProdusent(kafka = Kafka())
    }

    fun lagre(spørreundersøkelse: SerializableSpørreundersøkelse) {
        valkeyService.lagre(
            type = Type.SPØRREUNDERSØKELSE,
            nøkkel = spørreundersøkelse.id,
            verdi = Json.encodeToString(spørreundersøkelse),
        )
    }

    fun lagre(spørreundersøkelseAntallSvarDto: SpørreundersøkelseAntallSvarDto) {
        valkeyService.lagre(
            type = Type.ANTALL_SVAR_FOR_SPØRSMÅL,
            nøkkel = "${spørreundersøkelseAntallSvarDto.spørreundersøkelseId}-${spørreundersøkelseAntallSvarDto.spørsmålId}",
            verdi = spørreundersøkelseAntallSvarDto.antallSvar.toString(),
        )
    }

    fun lagre(
        spørreundersøkelseId: String,
        temaresultat: TemaResultatDto,
    ) {
        valkeyService.lagre(
            type = Type.SPØRREUNDERSØKELSE_RESULTAT,
            nøkkel = "$spørreundersøkelseId-${temaresultat.id}",
            verdi = Json.encodeToString(temaresultat),
        )
    }

    fun slett(spørreundersøkelse: SerializableSpørreundersøkelse) {
        logger.info("Sletter spørreundersøkelse med id: '${spørreundersøkelse.id}'")
        listOf(Type.SPØRREUNDERSØKELSE, Type.ANTALL_DELTAKERE).forEach {
            logger.info("Sletter type '$it' for spørreundersøkelse med id: '${spørreundersøkelse.id}'")
            valkeyService.slett(it, spørreundersøkelse.id)
        }
    }

    fun lagreSesjon(
        sesjonsId: UUID,
        spørreundersøkelseId: UUID,
    ) {
        valkeyService.lagre(Type.SESJON, sesjonsId.toString(), spørreundersøkelseId.toString())
    }

    fun lagreAntallDeltakere(
        spørreundersøkelseId: UUID,
        antallDeltakere: Int,
    ) {
        valkeyService.lagre(Type.ANTALL_DELTAKERE, spørreundersøkelseId.toString(), antallDeltakere.toString())
    }

    fun henteSpørreundersøkelse(spørreundersøkelseId: UUID): SerializableSpørreundersøkelse {
        val undersøkelse = valkeyService.hente(Type.SPØRREUNDERSØKELSE, spørreundersøkelseId.toString())?.let {
            Json.decodeFromString<SerializableSpørreundersøkelse>(it)
        } ?: throw Feil(
            feilmelding = "Ukjent spørreundersøkelse '$spørreundersøkelseId'",
            feilkode = HttpStatusCode.Forbidden,
        )

        logger.info("Hentet spørreundersøkelse med id '${undersøkelse.id}' og status '${undersøkelse.status}'")
        return undersøkelse
    }

    private fun SpørreundersøkelseStatus.kanVisesForVert() =
        when (this) {
            PÅBEGYNT, AVSLUTTET -> true
            else -> false
        }

    fun hentSpørreundersøkelseSomVert(spørreundersøkelseId: UUID): Spørreundersøkelse {
        val undersøkelse = henteSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId)
        return if (undersøkelse.status.kanVisesForVert()) {
            undersøkelse.tilDomene()
        } else {
            throw Feil(
                feilmelding = "Spørreundersøkelse med id '$spørreundersøkelseId' har feil status '${undersøkelse.status}'",
                feilkode = HttpStatusCode.Forbidden,
            )
        }
    }

    fun hentePågåendeSpørreundersøkelse(spørreundersøkelseId: UUID): Spørreundersøkelse {
        val undersøkelse = henteSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId)
        return when (undersøkelse.status) {
            PÅBEGYNT -> undersøkelse.tilDomene()
            AVSLUTTET -> throw Feil(
                feilmelding = "Spørreundersøkelse med id '$spørreundersøkelseId' er avsluttet",
                feilkode = HttpStatusCode.Gone,
            )
            else -> throw Feil(
                feilmelding = "Spørreundersøkelse med id '$spørreundersøkelseId' har feil status '${undersøkelse.status}'",
                feilkode = HttpStatusCode.Forbidden,
            )
        }
    }

    fun henteSpørreundersøkelseIdFraSesjon(sesjonsId: UUID): UUID? =
        valkeyService.hente(Type.SESJON, sesjonsId.toString())?.let {
            UUID.fromString(it)
        }

    fun hentAntallDeltakere(spørreundersøkelseId: UUID): Int =
        valkeyService.hente(Type.ANTALL_DELTAKERE, spørreundersøkelseId.toString())?.toInt() ?: 0

    fun hentAntallSvar(
        spørreundersøkelseId: UUID,
        spørsmålId: UUID,
    ): Int = valkeyService.hente(Type.ANTALL_SVAR_FOR_SPØRSMÅL, "$spørreundersøkelseId-$spørsmålId")?.toInt() ?: 0

    fun hentResultater(
        spørreundersøkelseId: UUID,
        temaId: Int,
    ): TemaResultatDto {
        val resultater =
            valkeyService.hente(Type.SPØRREUNDERSØKELSE_RESULTAT, "$spørreundersøkelseId-$temaId")?.let {
                Json.decodeFromString<TemaResultatDto>(it)
            } ?: throw Feil(
                feilmelding = "Ingen resultater for tema '$temaId' i spørreundersøkelse '$spørreundersøkelseId'",
                feilkode = HttpStatusCode.Forbidden,
            )
        logger.info("Hentet resultater for tema med id '$temaId med id '$spørreundersøkelseId'")
        return resultater
    }

    fun åpneTema(
        spørreundersøkelseId: UUID,
        temaId: Int,
    ) {
        valkeyService.lagre(Type.ER_TEMA_ÅPENT, "$spørreundersøkelseId-$temaId", "ja")
    }

    fun erTemaÅpent(
        spørreundersøkelseId: UUID,
        temaId: Int,
    ) = valkeyService.hente(Type.ER_TEMA_ÅPENT, "$spørreundersøkelseId-$temaId") != null

    fun erSpørsmålÅpent(
        spørreundersøkelseId: UUID,
        temaId: Int,
        spørsmålId: UUID,
    ): Boolean =
        valkeyService.hente(Type.ER_SPØRSMÅL_ÅPENT, "$spørreundersøkelseId-$spørsmålId") != null ||
            erTemaÅpent(spørreundersøkelseId = spørreundersøkelseId, temaId = temaId)

    fun sendSvar(
        spørreundersøkelseId: UUID,
        sesjonsId: UUID,
        spørsmålId: UUID,
        svarIder: List<UUID>,
    ) = spørreundersøkelseSvarProdusent.sendSvar(
        svar = SpørreundersøkelseSvarDTO(
            spørreundersøkelseId = spørreundersøkelseId.toString(),
            sesjonId = sesjonsId.toString(),
            spørsmålId = spørsmålId.toString(),
            svarIder = svarIder.map { it.toString() },
        ),
    )

    fun lukkTema(
        spørreundersøkelseId: UUID,
        temaId: Int,
    ) {
        spørreundersøkelseHendelseProdusent.sendHendelse(
            hendelse = StengTema(
                spørreundersøkelseId = spørreundersøkelseId.toString(),
                temaId = temaId,
            ),
        )
        valkeyService.lagre(Type.TEMA_STATUS, nøkkel = "$spørreundersøkelseId-$temaId", TemaStatus.STENGT.name)
    }

    fun erTemaStengt(
        spørreundersøkelseId: UUID,
        temaId: Int,
    ) = hentTemaStatus(spørreundersøkelseId = spørreundersøkelseId, temaId = temaId) == TemaStatus.STENGT

    private fun hentTemaStatus(
        spørreundersøkelseId: UUID,
        temaId: Int,
    ): TemaStatus? =
        valkeyService.hente(Type.TEMA_STATUS, nøkkel = "$spørreundersøkelseId-$temaId")?.let {
            TemaStatus.valueOf(it)
        }

    fun erAlleTemaerErStengt(spørreundersøkelse: Spørreundersøkelse): Boolean =
        spørreundersøkelse.temaer.all { tema ->
            TemaStatus.STENGT == hentTemaStatus(spørreundersøkelse.id, tema.id)
        }

    fun antallSvarPåSpørsmålMedFærrestBesvarelser(
        tema: Tema,
        spørreundersøkelse: Spørreundersøkelse,
    ): Int {
        val antallSvarPerSpørsmål = tema.spørsmål.map { spørsmål ->
            hentAntallSvar(
                spørreundersøkelseId = spørreundersøkelse.id,
                spørsmålId = spørsmål.id,
            )
        }

        val antallSvarPåSpørsmålMedFærrestBesvarelser = antallSvarPerSpørsmål.min()
        return antallSvarPåSpørsmålMedFærrestBesvarelser
    }
}
