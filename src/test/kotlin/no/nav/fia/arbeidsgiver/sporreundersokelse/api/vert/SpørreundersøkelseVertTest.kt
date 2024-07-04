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
import no.nav.fia.arbeidsgiver.helper.hentAntallSvarForSpørreundersøkelse
import no.nav.fia.arbeidsgiver.helper.hentAntallSvarForSpørsmål
import no.nav.fia.arbeidsgiver.helper.hentAntallSvarForTema
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
import no.nav.fia.arbeidsgiver.helper.vertHenterVirksomhetsnavn
import no.nav.fia.arbeidsgiver.helper.åpneTema
import no.nav.fia.arbeidsgiver.konfigurasjon.KafkaTopics
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.VERT_BASEPATH
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.IdentifiserbartSpørsmål
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.TemaDto
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.TemaStatus
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.tilDto
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.Spørreundersøkelse
import no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.SpørreundersøkelseHendelseProdusent.*
import no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.SpørreundersøkelseOppdateringKonsument.TemaResultater
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
    fun `skal ikke kunne laste vertssider uten riktig ad-gruppe`() {
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
                        audience = "azure:fia-arbeidsgiver",
                        issuerId = "azure",
                        claims = mapOf(
                            "NAVident" to "Z12345",
                            "groups" to listOf(
                                "ikke-riktig-gruppe"
                            )
                        )
                    ).serialize()
                )
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
        val spørreundersøkelse =
            kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId).tilDomene()

        runBlocking {
            fiaArbeidsgiverApi.vertHenterAntallDeltakere(
                vertId = spørreundersøkelse.vertId!!,
                spørreundersøkelseId = spørreundersøkelse.id
            ) shouldBe 0

            shouldFail {
                fiaArbeidsgiverApi.vertHenterAntallDeltakere(
                    vertId = UUID.randomUUID(),
                    spørreundersøkelseId = spørreundersøkelse.id
                )
            }
        }
    }

    @Test
    fun `vert skal kunne hente virksomhetsnavn`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val spørreundersøkelseDto =
            kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId).tilDomene()

        runBlocking {
            fiaArbeidsgiverApi.vertHenterVirksomhetsnavn(
                vertId = spørreundersøkelseDto.vertId!!,
                spørreundersøkelseId = spørreundersøkelseDto.id
            ) shouldBe spørreundersøkelseDto.virksomhetsNavn
        }
    }

    @Test
    fun `vert skal kunne hente antall deltakere i en undersøkelse`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val spørreundersøkelse =
            kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId).tilDomene()

        runBlocking {
            fiaArbeidsgiverApi.vertHenterAntallDeltakere(
                vertId = spørreundersøkelse.vertId!!,
                spørreundersøkelseId = spørreundersøkelse.id
            ) shouldBe 0

            val antallDeltakere = 5
            for (deltaker in 1..5)
                fiaArbeidsgiverApi.bliMed(spørreundersøkelseId = spørreundersøkelseId)

            fiaArbeidsgiverApi.vertHenterAntallDeltakere(
                vertId = spørreundersøkelse.vertId!!,
                spørreundersøkelseId = spørreundersøkelse.id
            ) shouldBe antallDeltakere
        }
    }

    @Test
    fun `vert skal kunne få ut oversikt over alle temaer i en spørreundersøkelse`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val spørreundersøkelseDto =
            kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId).tilDomene()

        runBlocking {
            val temaOversikt = fiaArbeidsgiverApi.hentTemaoversikt(
                vertId = spørreundersøkelseDto.vertId!!,
                spørreundersøkelseId = spørreundersøkelseDto.id
            )
            temaOversikt shouldHaveSize spørreundersøkelseDto.temaer.size
            temaOversikt shouldContainInOrder spørreundersøkelseDto.temaer.mapIndexed { index, it ->
                TemaDto(
                    temaId = it.id,
                    navn = it.navn ?: it.beskrivelse!!,
                    del = index + 1,
                    status = if (it.id == spørreundersøkelseDto.temaer.first().id) TemaStatus.ÅPNET else TemaStatus.IKKE_ÅPNET,
                    førsteSpørsmålId = it.spørsmål.first().id.toString(),
                    spørsmålOgSvaralternativer = it.spørsmål.map { it.tilDto() },
                    nesteTemaId = spørreundersøkelseDto.temaer.getOrNull(index + 1)?.id
                )
            }
        }
    }

    @Test
    fun `vert skal kunne hente riktig temastatus når man har åpnet alle spørsmål i tema 1 men ikke noen i tema 2`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val spørreundersøkelseDto =
            kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId).tilDomene()

        runBlocking {
            val førsteTema = spørreundersøkelseDto.temaer.first()
            førsteTema.spørsmål.forEach {
                spørreundersøkelseDto.åpneSpørsmål(
                    spørsmål = IdentifiserbartSpørsmål(
                        temaId = førsteTema.id,
                        spørsmålId = it.id.toString()
                    ),
                )
            }
            val temaOversikt = fiaArbeidsgiverApi.hentTemaoversikt(
                spørreundersøkelseId = spørreundersøkelseDto.id,
                vertId = spørreundersøkelseDto.vertId!!
            )
            temaOversikt shouldHaveSize spørreundersøkelseDto.temaer.size
            temaOversikt.first().status shouldBe TemaStatus.ALLE_SPØRSMÅL_ÅPNET
            temaOversikt[1].status shouldBe TemaStatus.ÅPNET
            temaOversikt.last().status shouldBe TemaStatus.IKKE_ÅPNET
        }
    }

    @Test
    fun `vert skal kunne hente riktig temastatus når man har åpnet alle spørsmål alle temaer`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val spørreundersøkelseDto =
            kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId).tilDomene()

        runBlocking {
            spørreundersøkelseDto.temaer.forEach { tema ->
                tema.spørsmål.forEach {
                    spørreundersøkelseDto.åpneSpørsmål(
                        spørsmål = IdentifiserbartSpørsmål(
                            temaId = tema.id,
                            spørsmålId = it.id.toString()
                        ),
                    )
                }
            }

            val temaOversikt = fiaArbeidsgiverApi.hentTemaoversikt(
                spørreundersøkelseId = spørreundersøkelseDto.id,
                vertId = spørreundersøkelseDto.vertId!!
            )
            temaOversikt shouldHaveSize spørreundersøkelseDto.temaer.size
            temaOversikt shouldContainInOrder spørreundersøkelseDto.temaer.mapIndexed { index, it ->
                TemaDto(
                    temaId = it.id,
                    del = index + 1,
                    navn = it.navn ?: it.beskrivelse!!,
                    status = TemaStatus.ALLE_SPØRSMÅL_ÅPNET,
                    førsteSpørsmålId = it.spørsmål.first().id.toString(),
                    spørsmålOgSvaralternativer = it.spørsmål.map { it.tilDto() },
                    nesteTemaId = spørreundersøkelseDto.temaer.getOrNull(index + 1)?.id
                )
            }
        }
    }

    @Test
    fun `vert skal kunne få ut oversikt over ett tema i en spørreundersøkelse`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val spørreundersøkelse =
            kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId).tilDomene()

        val temaRedusereSykefravær =
            spørreundersøkelse.temaer.first { it.id == TEMA_ID_FOR_REDUSERE_SYKEFRAVÆR }

        runBlocking {
            val temaOversikt = fiaArbeidsgiverApi.hentTemaoversiktForEttTema(
                spørreundersøkelseId = spørreundersøkelse.id,
                vertId = spørreundersøkelse.vertId!!,
                temaId = TEMA_ID_FOR_REDUSERE_SYKEFRAVÆR
            )
            temaOversikt shouldNotBe null
            temaOversikt.del shouldBe 2
            temaOversikt.navn shouldBe temaRedusereSykefravær.navn
            temaOversikt.førsteSpørsmålId shouldBe temaRedusereSykefravær.spørsmål.first().id.toString()
        }
    }

    @Test
    fun `vert skal kunne hente spørsmålsoversikt`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val spørreundersøkelse =
            kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId).tilDomene()

        runBlocking {
            val tema = spørreundersøkelse.temaer.first()
            val spørsmål = tema.spørsmål.first()
            val spørsmålsoversiktDto = spørreundersøkelse.åpneSpørsmål(
                spørsmål = IdentifiserbartSpørsmål(
                    temaId = tema.id,
                    spørsmålId = spørsmål.id.toString()
                )
            )
            spørsmålsoversiktDto.spørsmål.tekst shouldBe spørsmål.tekst
            spørsmålsoversiktDto.spørsmål.svaralternativer.first().svarId shouldBe spørsmål.svaralternativer.first().id.toString()
            spørsmålsoversiktDto.spørsmål.svaralternativer.last().svarId shouldBe spørsmål.svaralternativer.last().id.toString()
            spørsmålsoversiktDto.spørsmål.svaralternativer.first().svartekst shouldBe spørsmål.svaralternativer.first().svartekst
            spørsmålsoversiktDto.spørsmål.svaralternativer.last().svartekst shouldBe spørsmål.svaralternativer.last().svartekst
            spørsmålsoversiktDto.nesteSpørsmål?.spørsmålId shouldBe tema.spørsmål[1].id.toString()
            spørsmålsoversiktDto.nesteSpørsmål?.temaId shouldBe tema.id
            spørsmålsoversiktDto.temanummer shouldBe 1
            spørsmålsoversiktDto.antallTema shouldBe spørreundersøkelse.temaer.size
            spørsmålsoversiktDto.spørsmålnummer shouldBe 1
            spørsmålsoversiktDto.antallSpørsmål shouldBe tema.spørsmål.size
        }

        // på siste spørsmål
        runBlocking {
            val tema = spørreundersøkelse.temaer.last()
            val spørsmål = tema.spørsmål.last()
            val spørsmålsoversiktDto = spørreundersøkelse.åpneSpørsmål(
                spørsmål = IdentifiserbartSpørsmål(
                    temaId = tema.id,
                    spørsmålId = spørsmål.id.toString()
                )
            )
            spørsmålsoversiktDto.spørsmål.tekst shouldBe spørsmål.tekst
            spørsmålsoversiktDto.spørsmål.svaralternativer.first().svarId shouldBe spørsmål.svaralternativer.first().id.toString()
            spørsmålsoversiktDto.spørsmål.svaralternativer.first().svartekst shouldBe spørsmål.svaralternativer.first().svartekst
            spørsmålsoversiktDto.spørsmål.svaralternativer.last().svarId shouldBe spørsmål.svaralternativer.last().id.toString()
            spørsmålsoversiktDto.spørsmål.svaralternativer.last().svartekst shouldBe spørsmål.svaralternativer.last().svartekst
            spørsmålsoversiktDto.nesteSpørsmål shouldBe null
        }
    }

    @Test
    fun `vert skal kunne vite hvor mange som har svart på ett spørsmål`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val spørreundersøkelseDto =
            kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId).tilDomene()

        runBlocking {
            val førsteSpørsmål = IdentifiserbartSpørsmål(
                temaId = spørreundersøkelseDto.temaer.first().id,
                spørsmålId = spørreundersøkelseDto.temaer.first().spørsmål.first().id.toString()
            )

            spørreundersøkelseDto.åpneSpørsmål(spørsmål = førsteSpørsmål)
            fiaArbeidsgiverApi.hentAntallSvarForSpørsmål(
                spørsmål = førsteSpørsmål,
                spørreundersøkelseId = spørreundersøkelseDto.id,
                vertId = spørreundersøkelseDto.vertId!!
            ) shouldBe 0

            (1..5).forEach { antallSvar ->
                val bliMedDTO = fiaArbeidsgiverApi.bliMed(spørreundersøkelseId = spørreundersøkelseId)
                fiaArbeidsgiverApi.svarPåSpørsmål(
                    spørsmål = førsteSpørsmål,
                    svarIder = listOf(spørreundersøkelseDto.hentSpørsmålITema(førsteSpørsmål)?.svaralternativer?.first()?.id.toString()),
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
                    spørreundersøkelseId = spørreundersøkelseDto.id,
                    vertId = spørreundersøkelseDto.vertId!!

                ) shouldBe antallSvar
            }
        }
    }

    @Test
    fun `vert skal kunne lukke et tema, og det bør resultere i en kafkamelding`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val spørreundersøkelseDto =
            kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId).tilDomene()

        runBlocking {
            val temaId = spørreundersøkelseDto.temaer.first().id
            fiaArbeidsgiverApi.stengTema(
                spørreundersøkelseId = spørreundersøkelseDto.id,
                vertId = spørreundersøkelseDto.vertId!!, temaId = temaId
            )

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
        val spørreundersøkelse =
            kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId).tilDomene()
        val temaId = spørreundersøkelse.temaer.first().id

        runBlocking {
            val resultatRespons = fiaArbeidsgiverApi.hentResultater(
                temaId = temaId,
                spørreundersøkelseId = spørreundersøkelse.id,
                vertId = spørreundersøkelse.vertId!!

            )
            resultatRespons.status shouldBe HttpStatusCode.Forbidden
            fiaArbeidsgiverApi shouldContainLog "Ingen resultater for tema '$temaId'".toRegex()
        }
    }

    @Test
    fun `vert skal kunne hente temaresultat`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val spørreundersøkelse =
            kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId).tilDomene()
        val svarPerSpørsmål = 2

        kafka.sendResultatPåTema(
            spørreundersøkelseId = spørreundersøkelseId,
            antallSvarPerSpørsmål = svarPerSpørsmål,
            temaMedSpørsmålOgSvaralternativerDto = spørreundersøkelse.temaer.first()
        )

        runBlocking {
            val resultatRespons = fiaArbeidsgiverApi.hentResultater(
                temaId = spørreundersøkelse.temaer.first().id,
                spørreundersøkelseId = spørreundersøkelse.id,
                vertId = spørreundersøkelse.vertId!!

            ).body<TemaResultater>()

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
            kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId).tilDomene()

        runBlocking {
            val bliMedDTO = fiaArbeidsgiverApi.bliMed(spørreundersøkelseId = spørreundersøkelseId)
            val førsteSpørsmål = fiaArbeidsgiverApi.hentFørsteSpørsmål(bliMedDTO)
            fiaArbeidsgiverApi.hentSpørsmålSomDeltaker(
                bliMedDTO = bliMedDTO,
                spørsmål = førsteSpørsmål
            ) shouldBe null

            fiaArbeidsgiverApi.åpneTema(
                spørreundersøkelseId = spørreundersøkelseDto.id,
                vertId = spørreundersøkelseDto.vertId!!, temaId = førsteSpørsmål.temaId
            )
            fiaArbeidsgiverApi.hentSpørsmålSomDeltaker(
                bliMedDTO = bliMedDTO,
                spørsmål = førsteSpørsmål
            )?.spørsmål?.tekst shouldBe spørreundersøkelseDto.hentSpørsmålITema(førsteSpørsmål)?.tekst
        }
    }

    @Test
    fun `vert skal kunne se hvor mange som har fullført alle spørsmål i ett tema`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val spørreundersøkelseDto =
            kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId).tilDomene()

        runBlocking {
            val tema = spørreundersøkelseDto.temaer.first()
            fiaArbeidsgiverApi.åpneTema(
                spørreundersøkelseId = spørreundersøkelseDto.id,
                vertId = spørreundersøkelseDto.vertId!!, temaId = tema.id
            )

            kafka.sendAntallSvar(
                spørreundersøkelseId = spørreundersøkelseId.toString(),
                spørsmålId = tema.spørsmål.first().id.toString(),
                antallSvar = 5
            )

            var antallDeltakereSomHarFullførtTema = fiaArbeidsgiverApi.hentAntallSvarForTema(
                temaId = tema.id,
                spørreundersøkelseId = spørreundersøkelseDto.id,
                vertId = spørreundersøkelseDto.vertId!!

            )
            antallDeltakereSomHarFullførtTema shouldBe 0

            tema.spørsmål.forEachIndexed { index, spørsmål ->
                kafka.sendAntallSvar(
                    spørreundersøkelseId = spørreundersøkelseId.toString(),
                    spørsmålId = spørsmål.id.toString(),
                    antallSvar = if (index == 1) 5 else 1
                )
            }

            antallDeltakereSomHarFullførtTema = fiaArbeidsgiverApi.hentAntallSvarForTema(
                temaId = tema.id,
                spørreundersøkelseId = spørreundersøkelseDto.id,
                vertId = spørreundersøkelseDto.vertId!!
            )
            antallDeltakereSomHarFullførtTema shouldBe 1
        }
    }

    @Test
    fun `vert skal kunne se hvor mange som har fullført alle spørsmål i hele spørreundersøkelsen`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val spørreundersøkelseDto =
            kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId).tilDomene()

        runBlocking {
            val tema1 = spørreundersøkelseDto.temaer.first()
            fiaArbeidsgiverApi.åpneTema(
                spørreundersøkelseId = spørreundersøkelseDto.id,
                vertId = spørreundersøkelseDto.vertId!!, temaId = tema1.id
            )

            tema1.spørsmål.forEachIndexed { index, spørsmål ->
                kafka.sendAntallSvar(
                    spørreundersøkelseId = spørreundersøkelseId.toString(),
                    spørsmålId = spørsmål.id.toString(),
                    antallSvar = if (index == 1) 5 else 1
                )
            }
            val tema2 = spørreundersøkelseDto.temaer[1]
            fiaArbeidsgiverApi.åpneTema(
                spørreundersøkelseId = spørreundersøkelseDto.id,
                vertId = spørreundersøkelseDto.vertId!!, temaId = tema2.id
            )
            tema2.spørsmål.forEachIndexed { index, spørsmål ->
                kafka.sendAntallSvar(
                    spørreundersøkelseId = spørreundersøkelseId.toString(),
                    spørsmålId = spørsmål.id.toString(),
                    antallSvar = if (index == 1) 5 else 1
                )
            }
            val tema3 = spørreundersøkelseDto.temaer.last()
            fiaArbeidsgiverApi.åpneTema(
                spørreundersøkelseId = spørreundersøkelseDto.id,
                vertId = spørreundersøkelseDto.vertId!!, temaId = tema3.id
            )
            tema3.spørsmål.forEachIndexed { index, spørsmål ->
                kafka.sendAntallSvar(
                    spørreundersøkelseId = spørreundersøkelseId.toString(),
                    spørsmålId = spørsmål.id.toString(),
                    antallSvar = if (index == 1) 5 else 1
                )
            }

            val antallDeltakereSomHarFullført = fiaArbeidsgiverApi.hentAntallSvarForSpørreundersøkelse(
                spørreundersøkelseId = spørreundersøkelseDto.id,
                vertId = spørreundersøkelseDto.vertId!!
            )
            antallDeltakereSomHarFullført shouldBe 1
        }
    }
}

suspend fun Spørreundersøkelse.åpneSpørsmål(
    spørsmål: IdentifiserbartSpørsmål,
) = fiaArbeidsgiverApi.hentSpørsmålSomVert(
    spørsmål = spørsmål,
    spørreundersøkelseId = this.id,
    vertId = this.vertId!!
)
