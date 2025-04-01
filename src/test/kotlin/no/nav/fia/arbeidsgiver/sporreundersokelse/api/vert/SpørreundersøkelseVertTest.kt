package no.nav.fia.arbeidsgiver.sporreundersokelse.api.vert

import ia.felles.integrasjoner.kafkameldinger.spørreundersøkelse.SpørreundersøkelseStatus.AVSLUTTET
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import no.nav.fia.arbeidsgiver.helper.TestContainerHelper.Companion.applikasjon
import no.nav.fia.arbeidsgiver.helper.TestContainerHelper.Companion.authContainerHelper
import no.nav.fia.arbeidsgiver.helper.TestContainerHelper.Companion.kafka
import no.nav.fia.arbeidsgiver.helper.TestContainerHelper.Companion.shouldContainLog
import no.nav.fia.arbeidsgiver.helper.bliMed
import no.nav.fia.arbeidsgiver.helper.hentAntallSvarForSpørreundersøkelse
import no.nav.fia.arbeidsgiver.helper.hentAntallSvarForSpørsmål
import no.nav.fia.arbeidsgiver.helper.hentAntallSvarForTema
import no.nav.fia.arbeidsgiver.helper.hentFørsteSpørsmål
import no.nav.fia.arbeidsgiver.helper.hentResultater
import no.nav.fia.arbeidsgiver.helper.hentSpørsmålSomDeltaker
import no.nav.fia.arbeidsgiver.helper.hentTemaDto
import no.nav.fia.arbeidsgiver.helper.performGet
import no.nav.fia.arbeidsgiver.helper.stengTema
import no.nav.fia.arbeidsgiver.helper.svarPåSpørsmål
import no.nav.fia.arbeidsgiver.helper.vertHentOversikt
import no.nav.fia.arbeidsgiver.helper.vertHenterAntallDeltakere
import no.nav.fia.arbeidsgiver.helper.vertHenterSpørreundersøkelseKontekst
import no.nav.fia.arbeidsgiver.helper.vertHenterVirksomhetsnavn
import no.nav.fia.arbeidsgiver.helper.åpneTema
import no.nav.fia.arbeidsgiver.konfigurasjon.Topic
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.VERT_BASEPATH
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.IdentifiserbartSpørsmålDto
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.TemaDto
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.tilDto
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.tilSpørreundersøkelseKontekstDto
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.TemaStatus
import no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.SpørreundersøkelseHendelseProdusent.StengTema
import no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.SpørreundersøkelseOppdateringKonsument.TemaResultatDto
import org.junit.After
import org.junit.Before
import java.util.UUID
import kotlin.test.Test

class SpørreundersøkelseVertTest {
    private val topic = Topic.SPØRREUNDERSØKELSE_HENDELSE
    private val konsument = kafka.nyKonsument(consumerGroupId = topic.konsumentGruppe)

    companion object {
        const val TEMA_ID_FOR_REDUSERE_SYKEFRAVÆR = 1
    }

    @Before
    fun setUp() {
        konsument.subscribe(mutableListOf(topic.navn))
    }

    @After
    fun tearDown() {
        konsument.unsubscribe()
        konsument.close()
    }

    @Test
    fun `skal ikke kunne laste vertssider uten azure-token`() {
        val spørreundersøkelseId = UUID.randomUUID()

        runBlocking {
            applikasjon.performGet(url = "$VERT_BASEPATH/$spørreundersøkelseId/antall-deltakere")
                .status shouldBe HttpStatusCode.Unauthorized
        }
    }

    @Test
    fun `skal ikke kunne laste vertssider uten riktig ad-gruppe`() {
        val spørreundersøkelseId = UUID.randomUUID()

        runBlocking {
            applikasjon.performGet(
                url = "$VERT_BASEPATH/$spørreundersøkelseId/antall-deltakere",
            ) {
                header(
                    key = HttpHeaders.Authorization,
                    value = authContainerHelper.issueToken(
                        audience = "azure:fia-arbeidsgiver",
                        issuerId = "azure",
                        claims = mapOf(
                            "NAVident" to "Z12345",
                            "groups" to listOf(
                                "ikke-riktig-gruppe",
                            ),
                        ),
                    ).serialize(),
                )
            }.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    @Test
    fun `skal ikke kunne laste vertssider uten gyldig scopet azure-token`() {
        val spørreundersøkelseId = UUID.randomUUID()

        runBlocking {
            applikasjon.performGet(
                url = "$VERT_BASEPATH/$spørreundersøkelseId/antall-deltakere",
            ) {
                header(
                    key = HttpHeaders.Authorization,
                    value = authContainerHelper.issueToken(
                        issuerId = "azure",
                        audience = "azure:fia-arbeidsgiver-frontend",
                    ).serialize(),
                )
            }.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    @Test
    fun `vert skal kunne hente virksomhetsnavn`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val spørreundersøkelse = kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId).tilDomene()

        runBlocking {
            applikasjon.vertHenterVirksomhetsnavn(
                spørreundersøkelseId = spørreundersøkelse.id,
            ) shouldBe spørreundersøkelse.virksomhetsNavn
        }
    }

    @Test
    fun `vert skal kunne hente kontekst til en spørreundersøkelse`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val spørreundersøkelse = kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId).tilDomene()

        runBlocking {
            val kontekst = applikasjon.vertHenterSpørreundersøkelseKontekst(
                spørreundersøkelseId = spørreundersøkelse.id,
            )
            kontekst.type shouldBe spørreundersøkelse.tilSpørreundersøkelseKontekstDto().type
            kontekst.virksomhetsnavn shouldBe spørreundersøkelse.tilSpørreundersøkelseKontekstDto().virksomhetsnavn
            kontekst.samarbeidsnavn shouldBe "Navn på et samarbeid"
        }
    }

    @Test
    fun `vert skal kunne hente plan fra en evaluerings kontekst`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val spørreundersøkelse = kafka.sendEvaluering(spørreundersøkelseId).tilDomene()

        runBlocking {
            val kontekst = applikasjon.vertHenterSpørreundersøkelseKontekst(
                spørreundersøkelseId = spørreundersøkelse.id,
            )
            kontekst.type shouldBe "Evaluering"
            kontekst.virksomhetsnavn shouldBe spørreundersøkelse.virksomhetsNavn
            kontekst.samarbeidsnavn shouldBe "Navn på et samarbeid"
            kontekst.plan shouldNotBe null
            kontekst.plan shouldBe spørreundersøkelse.plan
        }
    }

    @Test
    fun `vert skal kunne hente antall deltakere i en undersøkelse`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val spørreundersøkelse =
            kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId).tilDomene()

        runBlocking {
            applikasjon.vertHenterAntallDeltakere(spørreundersøkelseId = spørreundersøkelse.id) shouldBe 0

            val antallDeltakere = 5
            repeat(antallDeltakere) { applikasjon.bliMed(spørreundersøkelseId = spørreundersøkelseId) }

            applikasjon.vertHenterAntallDeltakere(spørreundersøkelseId = spørreundersøkelse.id) shouldBe antallDeltakere
        }
    }

    @Test
    fun `vert skal kunne få ut oversikt over alle temaer i en spørreundersøkelse`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val spørreundersøkelse =
            kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId).tilDomene()

        runBlocking {
            val temaDtoList = applikasjon.vertHentOversikt(spørreundersøkelseId = spørreundersøkelse.id)
            temaDtoList shouldHaveSize spørreundersøkelse.temaer.size
            temaDtoList shouldContainInOrder spørreundersøkelse.temaer.mapIndexed { index, it ->
                TemaDto(
                    id = it.id,
                    navn = it.navn,
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
    fun `vert skal kunne få kategori på alle spørsmål i en evaluering`() {
        val spørreundersøkelseId = UUID.randomUUID()

        val spørreundersøkelse = kafka.sendSpørreundersøkelse(
            spørreundersøkelseId = spørreundersøkelseId,
            spørreundersøkelse = kafka.enStandardEvaluering(
                id = spørreundersøkelseId,
            ),
        )

        runBlocking {
            applikasjon.åpneTema(
                temaId = spørreundersøkelse.temaer.first().id,
                spørreundersøkelseId = spørreundersøkelseId,
            )

            val temaer = applikasjon.vertHentOversikt(spørreundersøkelseId = spørreundersøkelseId)
            temaer shouldHaveSize 2
            temaer.forEach { tema ->
                tema.spørsmål.forAll { spm ->
                    listOf("Utvikle IA-arbeidet", "Veien videre") shouldContain spm.kategori
                }
            }
        }
    }

    @Test
    fun `vert skal kunne hente riktig temastatus når man har åpnet tema 1 men ikke tema 2`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val spørreundersøkelse = kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId).tilDomene()

        runBlocking {
            applikasjon.åpneTema(
                temaId = spørreundersøkelse.temaer.first().id,
                spørreundersøkelseId = spørreundersøkelse.id,
            )
            val temaDtoList = applikasjon.vertHentOversikt(
                spørreundersøkelseId = spørreundersøkelse.id,
            )
            temaDtoList shouldHaveSize spørreundersøkelse.temaer.size
            temaDtoList[0].status shouldBe TemaStatus.ALLE_SPØRSMÅL_ÅPNET
            temaDtoList[1].status shouldBe TemaStatus.ÅPNET
            temaDtoList[2].status shouldBe TemaStatus.IKKE_ÅPNET
        }
    }

    @Test
    fun `vert skal kunne hente riktig temastatus når man har stengt tema 1`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val spørreundersøkelse = kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId).tilDomene()

        runBlocking {
            applikasjon.åpneTema(
                temaId = spørreundersøkelse.temaer.first().id,
                spørreundersøkelseId = spørreundersøkelse.id,
            )
            applikasjon.stengTema(
                temaId = spørreundersøkelse.temaer.first().id,
                spørreundersøkelseId = spørreundersøkelse.id,
            )

            val temaDtoList = applikasjon.vertHentOversikt(spørreundersøkelseId = spørreundersøkelse.id)
            temaDtoList shouldHaveSize spørreundersøkelse.temaer.size
            temaDtoList[0].status shouldBe TemaStatus.STENGT
            temaDtoList[1].status shouldBe TemaStatus.ÅPNET
            temaDtoList[2].status shouldBe TemaStatus.IKKE_ÅPNET
        }
    }

    @Test
    fun `vert skal kunne hente riktig temastatus når man har åpnet alle spørsmål alle temaer`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val spørreundersøkelse = kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId).tilDomene()

        runBlocking {
            spørreundersøkelse.temaer.forEach { tema ->
                applikasjon.åpneTema(
                    temaId = tema.id,
                    spørreundersøkelseId = spørreundersøkelse.id,
                )
            }

            val temaDtoList = applikasjon.vertHentOversikt(spørreundersøkelseId = spørreundersøkelse.id)
            temaDtoList shouldHaveSize spørreundersøkelse.temaer.size
            temaDtoList shouldContainInOrder spørreundersøkelse.temaer.mapIndexed { index, it ->
                TemaDto(
                    id = it.id,
                    del = index + 1,
                    navn = it.navn,
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
        val spørreundersøkelse = kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId).tilDomene()

        val temaRedusereSykefravær = spørreundersøkelse.temaer.first { it.id == TEMA_ID_FOR_REDUSERE_SYKEFRAVÆR }

        runBlocking {
            val temaDto = applikasjon.hentTemaDto(
                spørreundersøkelseId = spørreundersøkelse.id,
                temaId = TEMA_ID_FOR_REDUSERE_SYKEFRAVÆR,
            )
            temaDto shouldNotBe null
            temaDto.del shouldBe 2
            temaDto.navn shouldBe temaRedusereSykefravær.navn
            temaDto.førsteSpørsmålId shouldBe temaRedusereSykefravær.spørsmål.first().id.toString()
        }
    }

    @Test
    fun `vert skal kunne vite hvor mange som har svart på ett spørsmål`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val spørreundersøkelse = kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId).tilDomene()

        runBlocking {
            val førsteSpørsmål = IdentifiserbartSpørsmålDto(
                temaId = spørreundersøkelse.temaer.first().id,
                spørsmålId = spørreundersøkelse.temaer.first().spørsmål.first().id.toString(),
            )

            applikasjon.åpneTema(
                temaId = førsteSpørsmål.temaId,
                spørreundersøkelseId = spørreundersøkelse.id,
            )

            applikasjon.hentAntallSvarForSpørsmål(
                spørsmål = førsteSpørsmål,
                spørreundersøkelseId = spørreundersøkelse.id,
            ) shouldBe 0

            (1..5).forEach { antallSvar ->
                val bliMedDTO = applikasjon.bliMed(spørreundersøkelseId = spørreundersøkelseId)
                applikasjon.svarPåSpørsmål(
                    spørsmål = førsteSpørsmål,
                    svarIder = listOf(spørreundersøkelse.hentSpørsmålITema(førsteSpørsmål)?.svaralternativer?.first()?.id.toString()),
                    bliMedDTO = bliMedDTO,
                ) {
                    kafka.sendAntallSvar(
                        spørreundersøkelseId = spørreundersøkelseId.toString(),
                        spørsmålId = førsteSpørsmål.spørsmålId,
                        antallSvar = antallSvar,
                    )
                }
                applikasjon.hentAntallSvarForSpørsmål(
                    spørsmål = førsteSpørsmål,
                    spørreundersøkelseId = spørreundersøkelse.id,
                ) shouldBe antallSvar
            }
        }
    }

    @Test
    fun `vert skal kunne lukke et tema, og det bør resultere i en kafkamelding`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val spørreundersøkelse = kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId).tilDomene()

        runBlocking {
            val temaId = spørreundersøkelse.temaer.first().id
            applikasjon.stengTema(
                spørreundersøkelseId = spørreundersøkelse.id,
                temaId = temaId,
            )

            val stengTema = StengTema(spørreundersøkelseId.toString(), temaId)
            kafka.ventOgKonsumerKafkaMeldinger(stengTema.tilNøkkel(), konsument) { meldinger ->
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
            val resultatRespons = applikasjon.hentResultater(
                temaId = temaId,
                spørreundersøkelseId = spørreundersøkelse.id,
            )
            resultatRespons.status shouldBe HttpStatusCode.Forbidden
            applikasjon shouldContainLog "Ingen resultater for tema '$temaId'".toRegex()
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
            tema = spørreundersøkelse.temaer.first(),
        )

        runBlocking {
            val resultatRespons = applikasjon.hentResultater(
                temaId = spørreundersøkelse.temaer.first().id,
                spørreundersøkelseId = spørreundersøkelse.id,
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
            val bliMedDTO = applikasjon.bliMed(spørreundersøkelseId = spørreundersøkelseId)
            val førsteSpørsmål = applikasjon.hentFørsteSpørsmål(bliMedDTO)
            applikasjon.hentSpørsmålSomDeltaker(
                bliMedDTO = bliMedDTO,
                spørsmål = førsteSpørsmål,
            ) shouldBe null
            applikasjon.åpneTema(
                spørreundersøkelseId = spørreundersøkelse.id,
                temaId = førsteSpørsmål.temaId,
            )
            applikasjon.hentSpørsmålSomDeltaker(
                bliMedDTO = bliMedDTO,
                spørsmål = førsteSpørsmål,
            )?.spørsmål?.tekst shouldBe spørreundersøkelse.hentSpørsmålITema(førsteSpørsmål)?.tekst
        }
    }

    @Test
    fun `vert skal kunne se hvor mange som har fullført alle spørsmål i ett tema`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val spørreundersøkelse = kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId).tilDomene()

        runBlocking {
            val tema = spørreundersøkelse.temaer.first()
            applikasjon.åpneTema(
                spørreundersøkelseId = spørreundersøkelse.id,
                temaId = tema.id,
            )

            kafka.sendAntallSvar(
                spørreundersøkelseId = spørreundersøkelseId.toString(),
                spørsmålId = tema.spørsmål.first().id.toString(),
                antallSvar = 5,
            )

            var antallDeltakereSomHarFullførtTema = applikasjon.hentAntallSvarForTema(
                temaId = tema.id,
                spørreundersøkelseId = spørreundersøkelse.id,
            )
            antallDeltakereSomHarFullførtTema shouldBe 0

            tema.spørsmål.forEachIndexed { index, spørsmål ->
                kafka.sendAntallSvar(
                    spørreundersøkelseId = spørreundersøkelseId.toString(),
                    spørsmålId = spørsmål.id.toString(),
                    antallSvar = if (index == 1) 5 else 1,
                )
            }

            antallDeltakereSomHarFullførtTema = applikasjon.hentAntallSvarForTema(
                temaId = tema.id,
                spørreundersøkelseId = spørreundersøkelse.id,
            )
            antallDeltakereSomHarFullførtTema shouldBe 1
        }
    }

    @Test
    fun `vert skal kunne se hvor mange som har fullført alle spørsmål i hele spørreundersøkelsen`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val spørreundersøkelse = kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId).tilDomene()

        runBlocking {
            val førsteTema = spørreundersøkelse.temaer[0]
            val andreTema = spørreundersøkelse.temaer[1]
            val tredjeTema = spørreundersøkelse.temaer[2]
            applikasjon.åpneTema(
                spørreundersøkelseId = spørreundersøkelse.id,
                temaId = førsteTema.id,
            )

            førsteTema.spørsmål.forEachIndexed { index, spørsmål ->
                kafka.sendAntallSvar(
                    spørreundersøkelseId = spørreundersøkelseId.toString(),
                    spørsmålId = spørsmål.id.toString(),
                    antallSvar = if (index == 1) 5 else 1,
                )
            }
            applikasjon.åpneTema(
                spørreundersøkelseId = spørreundersøkelse.id,
                temaId = andreTema.id,
            )
            andreTema.spørsmål.forEachIndexed { index, spørsmål ->
                kafka.sendAntallSvar(
                    spørreundersøkelseId = spørreundersøkelseId.toString(),
                    spørsmålId = spørsmål.id.toString(),
                    antallSvar = if (index == 1) 5 else 1,
                )
            }
            applikasjon.åpneTema(
                spørreundersøkelseId = spørreundersøkelse.id,
                temaId = tredjeTema.id,
            )
            tredjeTema.spørsmål.forEachIndexed { index, spørsmål ->
                kafka.sendAntallSvar(
                    spørreundersøkelseId = spørreundersøkelseId.toString(),
                    spørsmålId = spørsmål.id.toString(),
                    antallSvar = if (index == 1) 5 else 1,
                )
            }

            val antallDeltakereSomHarFullført = applikasjon.hentAntallSvarForSpørreundersøkelse(
                spørreundersøkelseId = spørreundersøkelse.id,
            )
            antallDeltakereSomHarFullført shouldBe 1
        }
    }

    @Test
    fun `vert skal kunne hente en avsluttet spørreundersøkelse`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val pågåendeSpørreundersøkelse = kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId)
        val spørreundersøkelse = kafka.sendSpørreundersøkelse(
            spørreundersøkelseId = spørreundersøkelseId,
            spørreundersøkelse = pågåendeSpørreundersøkelse.copy(status = AVSLUTTET),
        ).tilDomene()

        runBlocking {
            val temaDtoList = applikasjon.vertHentOversikt(
                spørreundersøkelseId = spørreundersøkelse.id,
            )
            temaDtoList shouldHaveSize spørreundersøkelse.temaer.size
            temaDtoList shouldContainInOrder spørreundersøkelse.temaer.mapIndexed { index, it ->
                TemaDto(
                    id = it.id,
                    navn = it.navn,
                    del = index + 1,
                    status = if (it.id == spørreundersøkelse.temaer.first().id) TemaStatus.ÅPNET else TemaStatus.IKKE_ÅPNET,
                    førsteSpørsmålId = it.spørsmål.first().id.toString(),
                    nesteTemaId = spørreundersøkelse.temaer.elementAtOrNull(index + 1)?.id,
                    spørsmål = it.spørsmål.map { it.tilDto() },
                )
            }
        }
    }
}
