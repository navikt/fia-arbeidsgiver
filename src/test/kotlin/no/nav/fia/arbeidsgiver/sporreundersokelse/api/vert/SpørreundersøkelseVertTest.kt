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
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.IdentifiserbartSpørsmålDto
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.TemaDto
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.TemaStatus
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.tilDto
import no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.SpørreundersøkelseHendelseProdusent.*
import no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.SpørreundersøkelseOppdateringKonsument.TemaResultatDto
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
        val spørreundersøkelse =
            kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId).tilDomene()

        runBlocking {
            fiaArbeidsgiverApi.performGet(
                url = "$VERT_BASEPATH/$spørreundersøkelseId/antall-deltakere",
            ) {
                header(HEADER_VERT_ID, spørreundersøkelse.vertId)
            }.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    @Test
    fun `skal ikke kunne laste vertssider uten riktig ad-gruppe`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val spørreundersøkelse =
            kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId).tilDomene()

        runBlocking {
            fiaArbeidsgiverApi.performGet(
                url = "$VERT_BASEPATH/$spørreundersøkelseId/antall-deltakere",
            ) {
                header(HEADER_VERT_ID, spørreundersøkelse.vertId)
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
        val spørreundersøkelse = kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId).tilDomene()

        runBlocking {
            fiaArbeidsgiverApi.performGet(
                url = "$VERT_BASEPATH/$spørreundersøkelseId/antall-deltakere",
            ) {
                header(HEADER_VERT_ID, spørreundersøkelse.vertId)
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
        val spørreundersøkelse = kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId).tilDomene()

        runBlocking {
            fiaArbeidsgiverApi.vertHenterVirksomhetsnavn(
                vertId = spørreundersøkelse.vertId!!,
                spørreundersøkelseId = spørreundersøkelse.id
            ) shouldBe spørreundersøkelse.virksomhetsNavn
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
        val spørreundersøkelse =
            kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId).tilDomene()

        runBlocking {
            val temaOversikt = fiaArbeidsgiverApi.hentTemaoversikt(
                vertId = spørreundersøkelse.vertId!!,
                spørreundersøkelseId = spørreundersøkelse.id
            )
            temaOversikt shouldHaveSize spørreundersøkelse.temaer.size
            temaOversikt shouldContainInOrder spørreundersøkelse.temaer.mapIndexed { index, it ->
                TemaDto(
                    id = it.id,
                    navn = it.navn ?: it.beskrivelse!!,
                    del = index + 1,
                    status = if (it.id == spørreundersøkelse.temaer.first().id) TemaStatus.ÅPNET else TemaStatus.IKKE_ÅPNET,
                    førsteSpørsmålId = it.spørsmål.first().id.toString(),
                    nesteTemaId = spørreundersøkelse.temaer.elementAtOrNull(index + 1)?.id,
                    spørsmål = it.spørsmål.map { it.tilDto() },
                )
            }
        }
    }

    @Test
    fun `vert skal kunne hente riktig temastatus når man har åpnet tema 1 men ikke tema 2`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val spørreundersøkelse = kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId).tilDomene()

        runBlocking {
            fiaArbeidsgiverApi.åpneTema(
                temaId = spørreundersøkelse.temaer.first().id,
                spørreundersøkelseId = spørreundersøkelse.id,
                vertId = spørreundersøkelse.vertId!!
            )
            val temaOversikt = fiaArbeidsgiverApi.hentTemaoversikt(
                spørreundersøkelseId = spørreundersøkelse.id,
                vertId = spørreundersøkelse.vertId!!
            )
            temaOversikt shouldHaveSize spørreundersøkelse.temaer.size
            temaOversikt.first().status shouldBe TemaStatus.ALLE_SPØRSMÅL_ÅPNET
            temaOversikt[1].status shouldBe TemaStatus.ÅPNET
            temaOversikt.last().status shouldBe TemaStatus.IKKE_ÅPNET
        }
    }

    @Test
    fun `vert skal kunne hente riktig temastatus når man har åpnet alle spørsmål alle temaer`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val spørreundersøkelse =
            kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId).tilDomene()

        runBlocking {
            spørreundersøkelse.temaer.forEach { tema ->
                fiaArbeidsgiverApi.åpneTema(
                    temaId = tema.id,
                    spørreundersøkelseId = spørreundersøkelse.id,
                    vertId = spørreundersøkelse.vertId!!
                )
            }

            val temaOversikt = fiaArbeidsgiverApi.hentTemaoversikt(
                spørreundersøkelseId = spørreundersøkelse.id,
                vertId = spørreundersøkelse.vertId!!
            )
            temaOversikt shouldHaveSize spørreundersøkelse.temaer.size
            temaOversikt shouldContainInOrder spørreundersøkelse.temaer.mapIndexed { index, it ->
                TemaDto(
                    id = it.id,
                    del = index + 1,
                    navn = it.navn ?: it.beskrivelse!!,
                    status = TemaStatus.ALLE_SPØRSMÅL_ÅPNET,
                    nesteTemaId = spørreundersøkelse.temaer.elementAtOrNull(index + 1)?.id,
                    førsteSpørsmålId = it.spørsmål.first().id.toString(),
                    spørsmål = it.spørsmål.map { it.tilDto() },
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
    fun `vert skal kunne vite hvor mange som har svart på ett spørsmål`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val spørreundersøkelse = kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId).tilDomene()

        runBlocking {
            val førsteSpørsmål = IdentifiserbartSpørsmålDto(
                temaId = spørreundersøkelse.temaer.first().id,
                spørsmålId = spørreundersøkelse.temaer.first().spørsmål.first().id.toString()
            )

            fiaArbeidsgiverApi.åpneTema(
                temaId = førsteSpørsmål.temaId,
                spørreundersøkelseId = spørreundersøkelse.id,
                vertId = spørreundersøkelse.vertId!!
            )

            fiaArbeidsgiverApi.hentAntallSvarForSpørsmål(
                spørsmål = førsteSpørsmål,
                spørreundersøkelseId = spørreundersøkelse.id,
                vertId = spørreundersøkelse.vertId!!
            ) shouldBe 0

            (1..5).forEach { antallSvar ->
                val bliMedDTO = fiaArbeidsgiverApi.bliMed(spørreundersøkelseId = spørreundersøkelseId)
                fiaArbeidsgiverApi.svarPåSpørsmål(
                    spørsmål = førsteSpørsmål,
                    svarIder = listOf(spørreundersøkelse.hentSpørsmålITema(førsteSpørsmål)?.svaralternativer?.first()?.id.toString()),
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
                    spørreundersøkelseId = spørreundersøkelse.id,
                    vertId = spørreundersøkelse.vertId!!

                ) shouldBe antallSvar
            }
        }
    }

    @Test
    fun `vert skal kunne lukke et tema, og det bør resultere i en kafkamelding`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val spørreundersøkelse =kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId).tilDomene()

        runBlocking {
            val temaId = spørreundersøkelse.temaer.first().id
            fiaArbeidsgiverApi.stengTema(
                spørreundersøkelseId = spørreundersøkelse.id,
                vertId = spørreundersøkelse.vertId!!, temaId = temaId
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
            tema = spørreundersøkelse.temaer.first()
        )

        runBlocking {
            val resultatRespons = fiaArbeidsgiverApi.hentResultater(
                temaId = spørreundersøkelse.temaer.first().id,
                spørreundersøkelseId = spørreundersøkelse.id,
                vertId = spørreundersøkelse.vertId!!

            ).body<TemaResultatDto>()

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
        val spørreundersøkelse = kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId).tilDomene()

        runBlocking {
            val bliMedDTO = fiaArbeidsgiverApi.bliMed(spørreundersøkelseId = spørreundersøkelseId)
            val førsteSpørsmål = fiaArbeidsgiverApi.hentFørsteSpørsmål(bliMedDTO)
            fiaArbeidsgiverApi.hentSpørsmålSomDeltaker(
                bliMedDTO = bliMedDTO,
                spørsmål = førsteSpørsmål
            ) shouldBe null

            fiaArbeidsgiverApi.åpneTema(
                spørreundersøkelseId = spørreundersøkelse.id,
                vertId = spørreundersøkelse.vertId!!, temaId = førsteSpørsmål.temaId
            )
            fiaArbeidsgiverApi.hentSpørsmålSomDeltaker(
                bliMedDTO = bliMedDTO,
                spørsmål = førsteSpørsmål
            )?.spørsmål?.tekst shouldBe spørreundersøkelse.hentSpørsmålITema(førsteSpørsmål)?.tekst
        }
    }

    @Test
    fun `vert skal kunne se hvor mange som har fullført alle spørsmål i ett tema`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val spørreundersøkelse = kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId).tilDomene()

        runBlocking {
            val tema = spørreundersøkelse.temaer.first()
            fiaArbeidsgiverApi.åpneTema(
                spørreundersøkelseId = spørreundersøkelse.id,
                vertId = spørreundersøkelse.vertId!!, temaId = tema.id
            )

            kafka.sendAntallSvar(
                spørreundersøkelseId = spørreundersøkelseId.toString(),
                spørsmålId = tema.spørsmål.first().id.toString(),
                antallSvar = 5
            )

            var antallDeltakereSomHarFullførtTema = fiaArbeidsgiverApi.hentAntallSvarForTema(
                temaId = tema.id,
                spørreundersøkelseId = spørreundersøkelse.id,
                vertId = spørreundersøkelse.vertId!!

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
                spørreundersøkelseId = spørreundersøkelse.id,
                vertId = spørreundersøkelse.vertId!!
            )
            antallDeltakereSomHarFullførtTema shouldBe 1
        }
    }

    @Test
    fun `vert skal kunne se hvor mange som har fullført alle spørsmål i hele spørreundersøkelsen`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val spørreundersøkelse = kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId).tilDomene()

        runBlocking {
            val tema1 = spørreundersøkelse.temaer.first()
            fiaArbeidsgiverApi.åpneTema(
                spørreundersøkelseId = spørreundersøkelse.id,
                vertId = spørreundersøkelse.vertId!!, temaId = tema1.id
            )

            tema1.spørsmål.forEachIndexed { index, spørsmål ->
                kafka.sendAntallSvar(
                    spørreundersøkelseId = spørreundersøkelseId.toString(),
                    spørsmålId = spørsmål.id.toString(),
                    antallSvar = if (index == 1) 5 else 1
                )
            }
            val tema2 = spørreundersøkelse.temaer[1]
            fiaArbeidsgiverApi.åpneTema(
                spørreundersøkelseId = spørreundersøkelse.id,
                vertId = spørreundersøkelse.vertId!!, temaId = tema2.id
            )
            tema2.spørsmål.forEachIndexed { index, spørsmål ->
                kafka.sendAntallSvar(
                    spørreundersøkelseId = spørreundersøkelseId.toString(),
                    spørsmålId = spørsmål.id.toString(),
                    antallSvar = if (index == 1) 5 else 1
                )
            }
            val tema3 = spørreundersøkelse.temaer.last()
            fiaArbeidsgiverApi.åpneTema(
                spørreundersøkelseId = spørreundersøkelse.id,
                vertId = spørreundersøkelse.vertId!!, temaId = tema3.id
            )
            tema3.spørsmål.forEachIndexed { index, spørsmål ->
                kafka.sendAntallSvar(
                    spørreundersøkelseId = spørreundersøkelseId.toString(),
                    spørsmålId = spørsmål.id.toString(),
                    antallSvar = if (index == 1) 5 else 1
                )
            }

            val antallDeltakereSomHarFullført = fiaArbeidsgiverApi.hentAntallSvarForSpørreundersøkelse(
                spørreundersøkelseId = spørreundersøkelse.id,
                vertId = spørreundersøkelse.vertId!!
            )
            antallDeltakereSomHarFullført shouldBe 1
        }
    }
}
