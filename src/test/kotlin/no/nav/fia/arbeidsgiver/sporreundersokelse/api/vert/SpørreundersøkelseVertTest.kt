package no.nav.fia.arbeidsgiver.sporreundersokelse.api.vert

import HEADER_VERT_ID
import io.kotest.assertions.shouldFail
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import java.util.*
import kotlin.test.Test
import kotlinx.coroutines.runBlocking
import no.nav.fia.arbeidsgiver.helper.TestContainerHelper
import no.nav.fia.arbeidsgiver.helper.TestContainerHelper.Companion.fiaArbeidsgiverApi
import no.nav.fia.arbeidsgiver.helper.TestContainerHelper.Companion.kafka
import no.nav.fia.arbeidsgiver.helper.TestContainerHelper.Companion.shouldContainLog
import no.nav.fia.arbeidsgiver.helper.bliMed
import no.nav.fia.arbeidsgiver.helper.hentAntallSvarForSpørsmål
import no.nav.fia.arbeidsgiver.helper.hentFørsteSpørsmål
import no.nav.fia.arbeidsgiver.helper.hentResultater
import no.nav.fia.arbeidsgiver.helper.hentSpørsmålSomDeltaker
import no.nav.fia.arbeidsgiver.helper.hentSpørsmålSomVert
import no.nav.fia.arbeidsgiver.helper.hentTemaoversikt
import no.nav.fia.arbeidsgiver.helper.hentTemaoversiktForEttTema
import no.nav.fia.arbeidsgiver.helper.performGet
import no.nav.fia.arbeidsgiver.helper.stengTema
import no.nav.fia.arbeidsgiver.helper.svarPåSpørsmål
import no.nav.fia.arbeidsgiver.helper.vertHenterAntallDeltakere
import no.nav.fia.arbeidsgiver.helper.åpneTema
import no.nav.fia.arbeidsgiver.konfigurasjon.KafkaTopics
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.deltaker.hentSpørsmålITema
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.IdentifiserbartSpørsmål
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.vert.dto.TemaOversiktDto
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.vert.dto.TemaStatus
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.StengTema
import no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.dto.SpørreundersøkelseDto
import no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.dto.TemaMedSpørsmålOgSvar
import org.junit.After
import org.junit.Before

class SpørreundersøkelseVertTest {
    private val spørreundersøkelseHendelseKonsument =
        kafka.nyKonsument(topic = KafkaTopics.SPØRREUNDERSØKELSE_HENDELSE)

    @Before
    fun setUp() {
        spørreundersøkelseHendelseKonsument.subscribe(mutableListOf(KafkaTopics.SPØRREUNDERSØKELSE_HENDELSE.navnMedNamespace))
    }

    @After
    fun tearDown() {
        spørreundersøkelseHendelseKonsument.unsubscribe()
        spørreundersøkelseHendelseKonsument.close()
    }

    val TEMA_ID_FOR_REDUSERE_SYKEFRAVÆR = 1

    @Test
    fun `skal ikke kunne laste vertssider uten azure-token`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val spørreundersøkelseDto =
            kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId)

        runBlocking {
            fiaArbeidsgiverApi.performGet(
                url = "$VERT_BASEPATH/$spørreundersøkelseId/antall-deltakere",
            ) {
                header(HEADER_VERT_ID, spørreundersøkelseDto.vertId)
            }.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    @Test
    fun `skal ikke kunne laste vertssider uten gyldig scopet azure-token`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val spørreundersøkelseDto =
            kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId)

        runBlocking {
            fiaArbeidsgiverApi.performGet(
                url = "$VERT_BASEPATH/$spørreundersøkelseId/antall-deltakere",
            ) {
                header(HEADER_VERT_ID, spørreundersøkelseDto.vertId)
                header(
                    HttpHeaders.Authorization, TestContainerHelper.authServer.issueToken(
                        issuerId = "azure",
                        audience = "azure:fia-arbeidsgiver-frontend"
                    ).serialize()
                )
            }.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    @Test
    fun `vertssider skal ikke kunne hentes uten gyldig vertsId`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val spørreundersøkelseDto =
            kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId)

        runBlocking {
            fiaArbeidsgiverApi.vertHenterAntallDeltakere(
                vertId = spørreundersøkelseDto.vertId,
                spørreundersøkelseId = spørreundersøkelseDto.spørreundersøkelseId
            ) shouldBe 0

            shouldFail {
                fiaArbeidsgiverApi.vertHenterAntallDeltakere(
                    vertId = UUID.randomUUID().toString(),
                    spørreundersøkelseId = spørreundersøkelseDto.spørreundersøkelseId
                )
            }
        }
    }

    @Test
    fun `vert skal kunne hente antall deltakere i en undersøkelse`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val spørreundersøkelseDto =
            kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId)

        runBlocking {
            fiaArbeidsgiverApi.vertHenterAntallDeltakere(
                vertId = spørreundersøkelseDto.vertId,
                spørreundersøkelseId = spørreundersøkelseDto.spørreundersøkelseId
            ) shouldBe 0

            val antallDeltakere = 5
            for (deltaker in 1..5)
                fiaArbeidsgiverApi.bliMed(spørreundersøkelseId = spørreundersøkelseId)

            fiaArbeidsgiverApi.vertHenterAntallDeltakere(
                vertId = spørreundersøkelseDto.vertId,
                spørreundersøkelseId = spørreundersøkelseDto.spørreundersøkelseId
            ) shouldBe antallDeltakere
        }
    }

    @Test
    fun `vert skal kunne få ut oversikt over alle temaer i en spørreundersøkelse`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val spørreundersøkelseDto =
            kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId)

        runBlocking {
            val temaOversikt = fiaArbeidsgiverApi.hentTemaoversikt(spørreundersøkelseDto)
            temaOversikt shouldHaveSize spørreundersøkelseDto.temaMedSpørsmålOgSvaralternativer.size
            temaOversikt shouldContainInOrder spørreundersøkelseDto.temaMedSpørsmålOgSvaralternativer.mapIndexed { index, it ->
                TemaOversiktDto(
                    temaId = it.temaId,
                    tittel = it.temanavn.name,
                    del = index + 1,
                    temanavn = it.temanavn,
                    beskrivelse = it.beskrivelse,
                    introtekst = it.introtekst,
                    status = if (it.temaId == spørreundersøkelseDto.temaMedSpørsmålOgSvaralternativer.first().temaId) TemaStatus.ÅPNET else TemaStatus.IKKE_ÅPNET,
                    førsteSpørsmålId = it.spørsmålOgSvaralternativer.first().id,
                    spørsmålOgSvaralternativer = it.spørsmålOgSvaralternativer
                )
            }
        }
    }

    @Test
    fun `vert skal kunne hente riktig temastatus når man har åpnet alle spørsmål i tema 1 men ikke noen i tema 2`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val spørreundersøkelseDto =
            kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId)

        runBlocking {
            val førsteTema = spørreundersøkelseDto.temaMedSpørsmålOgSvaralternativer.first()
            førsteTema.spørsmålOgSvaralternativer.forEach {
                spørreundersøkelseDto.åpneSpørsmål(
                    spørsmål = IdentifiserbartSpørsmål(
                        temaId = førsteTema.temaId,
                        spørsmålId = it.id
                    ),
                )
            }
            val temaOversikt = fiaArbeidsgiverApi.hentTemaoversikt(spørreundersøkelseDto)
            temaOversikt shouldHaveSize spørreundersøkelseDto.temaMedSpørsmålOgSvaralternativer.size
            temaOversikt shouldContainInOrder spørreundersøkelseDto.temaMedSpørsmålOgSvaralternativer.mapIndexed { index, it ->
                TemaOversiktDto(
                    temaId = it.temaId,
                    tittel = it.temanavn.name,
                    del = index + 1,
                    temanavn = it.temanavn,
                    beskrivelse = it.beskrivelse,
                    introtekst = it.introtekst,
                    status = if (it.temaId == førsteTema.temaId) TemaStatus.ALLE_SPØRSMÅL_ÅPNET else TemaStatus.ÅPNET,
                    førsteSpørsmålId = it.spørsmålOgSvaralternativer.first().id,
                    spørsmålOgSvaralternativer = it.spørsmålOgSvaralternativer
                )
            }
        }
    }

    @Test
    fun `vert skal kunne hente riktig temastatus når man har åpnet alle spørsmål alle temaer`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val spørreundersøkelseDto =
            kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId)

        runBlocking {
            spørreundersøkelseDto.temaMedSpørsmålOgSvaralternativer.forEach { tema ->
                tema.spørsmålOgSvaralternativer.forEach {
                    spørreundersøkelseDto.åpneSpørsmål(
                        spørsmål = IdentifiserbartSpørsmål(
                            temaId = tema.temaId,
                            spørsmålId = it.id
                        ),
                    )
                }
            }

            val temaOversikt = fiaArbeidsgiverApi.hentTemaoversikt(spørreundersøkelseDto)
            temaOversikt shouldHaveSize spørreundersøkelseDto.temaMedSpørsmålOgSvaralternativer.size
            temaOversikt shouldContainInOrder spørreundersøkelseDto.temaMedSpørsmålOgSvaralternativer.mapIndexed { index, it ->
                TemaOversiktDto(
                    temaId = it.temaId,
                    tittel = it.temanavn.name,
                    del = index + 1,
                    temanavn = it.temanavn,
                    beskrivelse = it.beskrivelse,
                    introtekst = it.introtekst,
                    status = TemaStatus.ALLE_SPØRSMÅL_ÅPNET,
                    førsteSpørsmålId = it.spørsmålOgSvaralternativer.first().id,
                    spørsmålOgSvaralternativer = it.spørsmålOgSvaralternativer
                )
            }
        }
    }

    @Test
    fun `vert skal kunne få ut oversikt over ett tema i en spørreundersøkelse`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val spørreundersøkelseDto =
            kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId)

        val temaRedusereSykefravær =
            spørreundersøkelseDto.temaMedSpørsmålOgSvaralternativer.first { it.temaId == TEMA_ID_FOR_REDUSERE_SYKEFRAVÆR }

        runBlocking {
            val temaOversikt = fiaArbeidsgiverApi.hentTemaoversiktForEttTema(
                spørreundersøkelse = spørreundersøkelseDto,
                temaId = TEMA_ID_FOR_REDUSERE_SYKEFRAVÆR
            )
            temaOversikt shouldNotBe null
            temaOversikt.temanavn shouldBe temaRedusereSykefravær.temanavn
            temaOversikt.del shouldBe 2
            temaOversikt.beskrivelse shouldBe temaRedusereSykefravær.beskrivelse
            temaOversikt.introtekst shouldBe temaRedusereSykefravær.introtekst
            temaOversikt.førsteSpørsmålId shouldBe temaRedusereSykefravær.spørsmålOgSvaralternativer.first().id
        }
    }

    @Test
    fun `vert skal kunne hente spørsmålsoversikt`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val spørreundersøkelseDto =
            kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId)

        runBlocking {
            val tema = spørreundersøkelseDto.temaMedSpørsmålOgSvaralternativer.first()
            val spørsmål = tema.spørsmålOgSvaralternativer.first()
            val spørsmålsoversiktDto = spørreundersøkelseDto.åpneSpørsmål(
                spørsmål = IdentifiserbartSpørsmål(
                    temaId = tema.temaId,
                    spørsmålId = spørsmål.id
                )
            )
            spørsmålsoversiktDto.spørsmålTekst shouldBe spørsmål.spørsmål
            spørsmålsoversiktDto.svaralternativer shouldContainInOrder spørsmål.svaralternativer
            spørsmålsoversiktDto.nesteSpørsmål?.spørsmålId shouldBe tema.spørsmålOgSvaralternativer[1].id
            spørsmålsoversiktDto.nesteSpørsmål?.temaId shouldBe tema.temaId
            spørsmålsoversiktDto.temanummer shouldBe 1
            spørsmålsoversiktDto.antallTema shouldBe spørreundersøkelseDto.temaMedSpørsmålOgSvaralternativer.size
            spørsmålsoversiktDto.spørsmålnummer shouldBe 1
            spørsmålsoversiktDto.antallSpørsmål shouldBe tema.spørsmålOgSvaralternativer.size
        }

        // på siste spørsmål
        runBlocking {
            val tema = spørreundersøkelseDto.temaMedSpørsmålOgSvaralternativer.last()
            val spørsmål = tema.spørsmålOgSvaralternativer.last()
            val spørsmålsoversiktDto = spørreundersøkelseDto.åpneSpørsmål(
                spørsmål = IdentifiserbartSpørsmål(
                    temaId = tema.temaId,
                    spørsmålId = spørsmål.id
                )
            )
            spørsmålsoversiktDto.spørsmålTekst shouldBe spørsmål.spørsmål
            spørsmålsoversiktDto.svaralternativer shouldContainInOrder spørsmål.svaralternativer
            spørsmålsoversiktDto.nesteSpørsmål shouldBe null
        }
    }

    @Test
    fun `vert skal kunne vite hvor mange som har svart på ett spørsmål`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val spørreundersøkelseDto =
            kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId)

        runBlocking {
            val førsteSpørsmål = IdentifiserbartSpørsmål(
                temaId = spørreundersøkelseDto.temaMedSpørsmålOgSvaralternativer.first().temaId,
                spørsmålId = spørreundersøkelseDto.temaMedSpørsmålOgSvaralternativer.first().spørsmålOgSvaralternativer.first().id
            )

            spørreundersøkelseDto.åpneSpørsmål(spørsmål = førsteSpørsmål)
            fiaArbeidsgiverApi.hentAntallSvarForSpørsmål(
                spørsmål = førsteSpørsmål,
                spørreundersøkelse = spørreundersøkelseDto
            ) shouldBe 0

            (1..5).forEach { antallSvar ->
                val bliMedDTO = fiaArbeidsgiverApi.bliMed(spørreundersøkelseId = spørreundersøkelseId)
                fiaArbeidsgiverApi.svarPåSpørsmål(
                    spørsmål = førsteSpørsmål,
                    svarIder = listOf(spørreundersøkelseDto.hentSpørsmålITema(førsteSpørsmål).svaralternativer.first().svarId),
                    bliMedDTO = bliMedDTO,
                ) {
                    kafka.sendAntallSvar(
                        spørreundersøkelseId = spørreundersøkelseId.toString(),
                        spørsmålId = førsteSpørsmål.spørsmålId,
                        antallSvar = antallSvar
                    )
                }

                fiaArbeidsgiverApi.hentAntallSvarForSpørsmål(
                    spørsmål = førsteSpørsmål,
                    spørreundersøkelse = spørreundersøkelseDto
                ) shouldBe antallSvar
            }
        }
    }

    @Test
    fun `vert skal kunne lukke et tema, og det bør resultere i en kafkamelding`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val spørreundersøkelseDto =
            kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId)

        runBlocking {
            val temaId = spørreundersøkelseDto.temaMedSpørsmålOgSvaralternativer.first().temaId
            fiaArbeidsgiverApi.stengTema(spørreundersøkelse = spørreundersøkelseDto, temaId = temaId)

            val stengTema = StengTema(spørreundersøkelseId.toString(), temaId)
            kafka.ventOgKonsumerKafkaMeldinger(stengTema.tilNøkkel(), spørreundersøkelseHendelseKonsument)
            { meldinger ->
                meldinger.forAll {
                    it.toInt() shouldBe temaId
                }
            }
        }
    }

    @Test
    fun `vert skal ikke kunne hente temaresultat før det er publisert i kafka`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val spørreundersøkelseDto =
            kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId)
        val temaId = spørreundersøkelseDto.temaMedSpørsmålOgSvaralternativer.first().temaId

        runBlocking {
            val resultatRespons = fiaArbeidsgiverApi.hentResultater(
                temaId = temaId,
                spørreundersøkelse = spørreundersøkelseDto
            )
            resultatRespons.status shouldBe HttpStatusCode.Forbidden
            fiaArbeidsgiverApi shouldContainLog "Ingen resultater for tema '$temaId'".toRegex()
        }
    }

    @Test
    fun `vert skal kunne hente temaresultat`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val spørreundersøkelseDto =
            kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId)
        val svarPerSpørsmål = 2

        kafka.sendResultatPåTema(
            spørreundersøkelseId = spørreundersøkelseId,
            antallSvarPerSpørsmål = svarPerSpørsmål,
            temaMedSpørsmålOgSvaralternativerDto = spørreundersøkelseDto.temaMedSpørsmålOgSvaralternativer.first()
        )

        runBlocking {
            val resultatRespons = fiaArbeidsgiverApi.hentResultater(
                temaId = spørreundersøkelseDto.temaMedSpørsmålOgSvaralternativer.first().temaId,
                spørreundersøkelse = spørreundersøkelseDto
            ).body<TemaMedSpørsmålOgSvar>()

            resultatRespons.spørsmålMedSvar.map { spørsmål ->
                spørsmål.svarListe.forEach {
                    it.antallSvar shouldBe svarPerSpørsmål
                }
            }
        }
    }

    @Test
    fun `vert skal kunne åpne ett tema`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val spørreundersøkelseDto =
            kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId)

        runBlocking {
            val bliMedDTO = fiaArbeidsgiverApi.bliMed(spørreundersøkelseId = spørreundersøkelseId)
            val førsteSpørsmål = fiaArbeidsgiverApi.hentFørsteSpørsmål(bliMedDTO)
            fiaArbeidsgiverApi.hentSpørsmålSomDeltaker(
                bliMedDTO = bliMedDTO,
                spørsmål = førsteSpørsmål
            ) shouldBe null

            fiaArbeidsgiverApi.åpneTema(spørreundersøkelse = spørreundersøkelseDto, temaId = førsteSpørsmål.temaId)
            fiaArbeidsgiverApi.hentSpørsmålSomDeltaker(
                bliMedDTO = bliMedDTO,
                spørsmål = førsteSpørsmål
            )?.spørsmålTekst shouldBe spørreundersøkelseDto.hentSpørsmålITema(førsteSpørsmål).spørsmål
        }
    }
}

suspend fun SpørreundersøkelseDto.åpneSpørsmål(
    spørsmål: IdentifiserbartSpørsmål,
) = fiaArbeidsgiverApi.hentSpørsmålSomVert(
    spørsmål = spørsmål,
    spørreundersøkelse = this
)
