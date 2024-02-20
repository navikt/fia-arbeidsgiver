package no.nav.api.sporreundersokelse

import io.kotest.inspectors.forAll
import io.kotest.inspectors.forAtLeastOne
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldHaveLength
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import no.nav.helper.TestContainerHelper
import no.nav.helper.performPost
import java.util.*
import kotlin.test.Test
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import no.nav.domene.sporreundersokelse.Spørreundersøkelse
import no.nav.domene.sporreundersokelse.SpørreundersøkelseStatus
import no.nav.helper.TestContainerHelper.Companion.shouldContainLog
import no.nav.helper.bliMed
import no.nav.kafka.SpørreundersøkelseSvar
import no.nav.kafka.Topic
import no.nav.persistence.KategoristatusDTO
import no.nav.persistence.KategoristatusDTO.Kategori.PARTSSAMARBEID
import no.nav.persistence.KategoristatusDTO.Status.OPPRETTET
import org.junit.After
import org.junit.Before

class SpørreundersøkelseApiTest {
    private val spørreundersøkelseSvarKonsument =
        TestContainerHelper.kafka.nyKonsument(topic = Topic.SPØRREUNDERSØKELSE_SVAR)

    @Before
    fun setUp() {
        spørreundersøkelseSvarKonsument.subscribe(mutableListOf(Topic.SPØRREUNDERSØKELSE_SVAR.navnMedNamespace))
    }

    @After
    fun tearDown() {
        spørreundersøkelseSvarKonsument.unsubscribe()
        spørreundersøkelseSvarKonsument.close()
    }

    @Test
    fun `skal kunne starte spørreundersøkelse`() {
        val spørreundersøkelseId = UUID.randomUUID()
        TestContainerHelper.kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId)

        runBlocking {
            val bliMedDTO = TestContainerHelper.fiaArbeidsgiverApi.bliMed(spørreundersøkelseId = spørreundersøkelseId)
            bliMedDTO.spørreundersøkelseId shouldBe spørreundersøkelseId.toString()
            bliMedDTO.sesjonsId shouldHaveLength UUID.randomUUID().toString().length
        }
    }

    @Test
    fun `skal kunne starte spørreundersøkelse dersom pin er med`() { //Gir svar på om vi støtter ukjente felter i requestene våre
        val spørreundersøkelseId = UUID.randomUUID()
        TestContainerHelper.kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId)

        runBlocking {
            val response = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = BLI_MED_PATH,
                body = BliMedRequestMedPin(spørreundersøkelseId = spørreundersøkelseId.toString(), pinkode = "654321")
            )
            response.status shouldBe HttpStatusCode.OK
        }
    }

    @Suppress("unused")
    @Serializable
    class BliMedRequestMedPin(val spørreundersøkelseId: String, val pinkode: String)

    @Test
    fun `returnerer BAD_REQUEST dersom UUID er feil formatert`() {

        runBlocking {
            val response = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = BLI_MED_PATH,
                body = BliMedRequest(spørreundersøkelseId = "tullogtøys")
            )
            response.status shouldBe HttpStatusCode.BadRequest
            TestContainerHelper.fiaArbeidsgiverApi shouldContainLog "Ugyldig formatert UUID".toRegex()
            val body = response.bodyAsText()

            body shouldBe ""
        }
    }

    @Test
    fun `deltaker skal kunne hente spørsmål og svar`() {
        val spørreundersøkelseId = UUID.randomUUID()
        TestContainerHelper.kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId)

        runBlocking {
            val bliMedDTO = TestContainerHelper.fiaArbeidsgiverApi.bliMed(spørreundersøkelseId = spørreundersøkelseId)

            val spørsmålOgSvarRespons = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = SPØRSMÅL_OG_SVAR_PATH,
                body = SpørsmålOgSvaralternativerRequest(
                    spørreundersøkelseId = spørreundersøkelseId.toString(),
                    sesjonsId = bliMedDTO.sesjonsId
                )
            )
            spørsmålOgSvarRespons.status shouldBe HttpStatusCode.OK
            val body = spørsmålOgSvarRespons.bodyAsText()
            val spørsmålOgSvaralternativer = Json.decodeFromString<List<SpørsmålOgSvaralternativerDTO>>(body)

            spørsmålOgSvaralternativer shouldHaveSize 2
            spørsmålOgSvaralternativer.first().svaralternativer shouldHaveSize 2
        }
    }

    @Test
    fun `deltaker skal kunne hente neste spørsmålId`() {
        val spørreundersøkelseId = UUID.randomUUID()
        TestContainerHelper.kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId)

        runBlocking {
            val bliMedDTO = TestContainerHelper.fiaArbeidsgiverApi.bliMed(spørreundersøkelseId = spørreundersøkelseId)
            val spørsmålOgSvarRespons = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = SPØRSMÅL_OG_SVAR_PATH,
                body = SpørsmålOgSvaralternativerRequest(
                    spørreundersøkelseId = spørreundersøkelseId.toString(),
                    sesjonsId = bliMedDTO.sesjonsId
                )
            )
            spørsmålOgSvarRespons.status shouldBe HttpStatusCode.OK
            val body = spørsmålOgSvarRespons.bodyAsText()
            val spørsmålOgSvaralternativer = Json.decodeFromString<List<SpørsmålOgSvaralternativerDTO>>(body)
            spørsmålOgSvaralternativer shouldHaveSize 2

            val idTilFørsteSpørsmål = spørsmålOgSvaralternativer.first().id
            val idTilAndreSpørsmål = spørsmålOgSvaralternativer.last().id

            val hvaErNesteSpørsmålRespons0 = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = NESTE_SPØRSMÅL_PATH,
                body = NesteSpørsmålRequest(
                    spørreundersøkelseId = spørreundersøkelseId.toString(),
                    sesjonsId = bliMedDTO.sesjonsId,
                    nåværrendeSpørsmålId = "Start",
                )
            )
            hvaErNesteSpørsmålRespons0.status shouldBe HttpStatusCode.OK
            val nesteSpørsmålDTO = Json.decodeFromString<NesteSpørsmålDTO>(hvaErNesteSpørsmålRespons0.bodyAsText())
            nesteSpørsmålDTO.nesteSpørsmålId shouldBe idTilFørsteSpørsmål.toString()
            nesteSpørsmålDTO.erNesteÅpnetAvVert shouldBe false
            nesteSpørsmålDTO.hvaErNesteSteg shouldBe NesteSpørsmålDTO.StegStatus.NYTT_SPØRSMÅL
            nesteSpørsmålDTO.forrigeSpørsmålId shouldBe null

            val hvaErNesteSpørsmålRespons1 = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = NESTE_SPØRSMÅL_PATH,
                body = NesteSpørsmålRequest(
                    spørreundersøkelseId = spørreundersøkelseId.toString(),
                    sesjonsId = bliMedDTO.sesjonsId,
                    nåværrendeSpørsmålId = idTilFørsteSpørsmål.toString(),
                )
            )
            hvaErNesteSpørsmålRespons1.status shouldBe HttpStatusCode.OK
            val nesteSpørsmålDTO1 = Json.decodeFromString<NesteSpørsmålDTO>(hvaErNesteSpørsmålRespons1.bodyAsText())
            nesteSpørsmålDTO1.nesteSpørsmålId shouldBe idTilAndreSpørsmål.toString()
            nesteSpørsmålDTO1.erNesteÅpnetAvVert shouldBe false
            nesteSpørsmålDTO1.hvaErNesteSteg shouldBe NesteSpørsmålDTO.StegStatus.NYTT_SPØRSMÅL
            nesteSpørsmålDTO1.forrigeSpørsmålId shouldBe null

            val hvaErNesteSpørsmålRespons2 = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = NESTE_SPØRSMÅL_PATH,
                body = NesteSpørsmålRequest(
                    spørreundersøkelseId = spørreundersøkelseId.toString(),
                    sesjonsId = bliMedDTO.sesjonsId,
                    nåværrendeSpørsmålId = idTilAndreSpørsmål.toString(),
                )
            )
            hvaErNesteSpørsmålRespons2.status shouldBe HttpStatusCode.OK
            val nesteSpørsmålDTO2 = Json.decodeFromString<NesteSpørsmålDTO>(hvaErNesteSpørsmålRespons2.bodyAsText())
            nesteSpørsmålDTO2.nesteSpørsmålId shouldBe null
            nesteSpørsmålDTO2.erNesteÅpnetAvVert shouldBe false
            nesteSpørsmålDTO2.hvaErNesteSteg shouldBe NesteSpørsmålDTO.StegStatus.FERDIG
            nesteSpørsmålDTO2.forrigeSpørsmålId shouldBe idTilFørsteSpørsmål.toString()
        }
    }

    @Test
    fun `deltaker skal kunne hente et spørsmål på en Id`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val spørreundersøkelse = TestContainerHelper.kafka.enStandardSpørreundersøkelse(spørreundersøkelseId)
        TestContainerHelper.kafka.sendSpørreundersøkelse(
            spørreundersøkelseId = spørreundersøkelseId,
            spørreundersøkelsesStreng = spørreundersøkelse.toJson()
        )

        runBlocking {
            val bliMedDTO = TestContainerHelper.fiaArbeidsgiverApi.bliMed(spørreundersøkelseId = spørreundersøkelseId)
            val førsteSpørsmål = spørreundersøkelse.spørsmålOgSvaralternativer.first()
            val spørsmålOgSvarRespons = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = "$SPØRSMÅL_OG_SVAR_PATH/${førsteSpørsmål.id}",
                body = SpørsmålOgSvaralternativerRequest(
                    spørreundersøkelseId = spørreundersøkelseId.toString(),
                    sesjonsId = bliMedDTO.sesjonsId
                )
            )
            spørsmålOgSvarRespons.status shouldBe HttpStatusCode.OK
            val body = spørsmålOgSvarRespons.bodyAsText()
            val spørsmålOgSvaralternativer = Json.decodeFromString<SpørsmålOgSvaralternativerDTO>(body)
            spørsmålOgSvaralternativer.id shouldBe førsteSpørsmål.id
        }
    }

    @Test
    fun `deltaker får NOT_FOUND dersom spørsmål er ikke funnet`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val spørreundersøkelse = TestContainerHelper.kafka.enStandardSpørreundersøkelse(spørreundersøkelseId)
        TestContainerHelper.kafka.sendSpørreundersøkelse(
            spørreundersøkelseId = spørreundersøkelseId,
            spørreundersøkelsesStreng = spørreundersøkelse.toJson()
        )

        runBlocking {
            val bliMedDTO = TestContainerHelper.fiaArbeidsgiverApi.bliMed(spørreundersøkelseId = spørreundersøkelseId)
            val spørsmålIdSomIkkeFinnes = UUID.randomUUID()
            val spørsmålOgSvarRespons = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = "$SPØRSMÅL_OG_SVAR_PATH/$spørsmålIdSomIkkeFinnes",
                body = SpørsmålOgSvaralternativerRequest(
                    spørreundersøkelseId = spørreundersøkelseId.toString(),
                    sesjonsId = bliMedDTO.sesjonsId
                )
            )
            spørsmålOgSvarRespons.status shouldBe HttpStatusCode.NotFound
            TestContainerHelper.fiaArbeidsgiverApi shouldContainLog "Spørsmål med id $spørsmålIdSomIkkeFinnes ble ikke funnet".toRegex()
        }
    }

    @Test
    fun `vert skal kunne hente spørsmål og svar`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val vertId = UUID.randomUUID()
        TestContainerHelper.kafka.enStandardSpørreundersøkelse(
            spørreundersøkelseId = spørreundersøkelseId,
            vertId = vertId
        )
            .also { spørreundersøkelse ->
                TestContainerHelper.kafka.sendSpørreundersøkelse(
                    spørreundersøkelseId = spørreundersøkelseId,
                    spørreundersøkelsesStreng = spørreundersøkelse.toJson()
                )
            }

        runBlocking {
            val spørsmålOgSvarRespons = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = VERT_SPØRSMÅL_OG_SVAR_PATH,
                body = VertshandlingRequest(
                    spørreundersøkelseId = spørreundersøkelseId.toString(),
                    vertId = vertId.toString()
                )
            )

            spørsmålOgSvarRespons.status shouldBe HttpStatusCode.OK
            val body = spørsmålOgSvarRespons.bodyAsText()
            val spørsmålOgSvaralternativer = Json.decodeFromString<List<SpørsmålOgSvaralternativerDTO>>(body)

            spørsmålOgSvaralternativer shouldHaveSize 2
            spørsmålOgSvaralternativer.first().svaralternativer shouldHaveSize 2
        }
    }

    @Test
    fun `skal ikke få spørsmål og svar dersom sesjonsId er ukjent`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val sesjonsId = UUID.randomUUID()
        TestContainerHelper.kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId)

        runBlocking {
            val spørsmålOgSvarRespons = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = SPØRSMÅL_OG_SVAR_PATH,
                body = SpørsmålOgSvaralternativerRequest(
                    spørreundersøkelseId = spørreundersøkelseId.toString(),
                    sesjonsId = sesjonsId.toString()
                )
            )
            spørsmålOgSvarRespons.status shouldBe HttpStatusCode.Forbidden
            TestContainerHelper.fiaArbeidsgiverApi shouldContainLog "Ugyldig sesjonsId".toRegex()
        }
    }

    @Test
    fun `skal kunne sende inn et gyldig svar`() {
        val spørreundersøkelseId = UUID.randomUUID()
        TestContainerHelper.kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId)

        runBlocking {
            val bliMedDTO = TestContainerHelper.fiaArbeidsgiverApi.bliMed(spørreundersøkelseId = spørreundersøkelseId)

            val spørsmålOgSvarRespons = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = SPØRSMÅL_OG_SVAR_PATH,
                body = SpørsmålOgSvaralternativerRequest(
                    spørreundersøkelseId = spørreundersøkelseId.toString(),
                    sesjonsId = bliMedDTO.sesjonsId
                )
            )
            spørsmålOgSvarRespons.status shouldBe HttpStatusCode.OK
            val body = spørsmålOgSvarRespons.bodyAsText()
            val spørsmålOgSvaralternativer = Json.decodeFromString<List<SpørsmålOgSvaralternativerDTO>>(body)

            val spørsmål = spørsmålOgSvaralternativer.first()
            val svaralternativ = spørsmål.svaralternativer.first()

            val svarRespons = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = SVAR_PATH,
                body = SvarRequest(
                    spørreundersøkelseId = spørreundersøkelseId.toString(),
                    sesjonsId = bliMedDTO.sesjonsId,
                    spørsmålId = spørsmål.id.toString(),
                    svarId = svaralternativ.id.toString()
                )
            )
            svarRespons.status shouldBe HttpStatusCode.OK
            TestContainerHelper.kafka.ventOgKonsumerKafkaMeldinger(
                key = "${bliMedDTO.sesjonsId}_${spørsmål.id}",
                konsument = spørreundersøkelseSvarKonsument
            ) { meldinger ->
                val objektene = meldinger.map {
                    Json.decodeFromString<SpørreundersøkelseSvar>(it)
                }
                objektene shouldHaveAtLeastSize 1
                objektene.forAtLeastOne {
                    it.spørreundersøkelseId shouldBe spørreundersøkelseId.toString()
                    it.sesjonId shouldBe bliMedDTO.sesjonsId
                    it.spørsmålId shouldBe spørsmål.id.toString()
                    it.svarId shouldBe svaralternativ.id.toString()
                }
            }
        }
    }

    @Test
    fun `skal få feilkode ved ukjent spørreundersøkelse`() {
        val spørreundersøkelseId = UUID.randomUUID()
        TestContainerHelper.kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId)

        runBlocking {
            val bliMedDTO = TestContainerHelper.fiaArbeidsgiverApi.bliMed(spørreundersøkelseId = spørreundersøkelseId)

            val svarRespons = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = SVAR_PATH,
                body = SvarRequest(
                    spørreundersøkelseId = UUID.randomUUID().toString(),
                    sesjonsId = bliMedDTO.sesjonsId,
                    spørsmålId = UUID.randomUUID().toString(),
                    svarId = UUID.randomUUID().toString()
                )
            )
            svarRespons.status shouldBe HttpStatusCode.Forbidden
            TestContainerHelper.fiaArbeidsgiverApi shouldContainLog "Ugyldig sesjonsId".toRegex()
        }
    }

    @Test
    fun `skal få feilkode ved ukjent svar og svaralternativ`() {
        val spørreundersøkelseId = UUID.randomUUID()
        TestContainerHelper.kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId)

        runBlocking {
            val bliMedDTO = TestContainerHelper.fiaArbeidsgiverApi.bliMed(spørreundersøkelseId = spørreundersøkelseId)

            val spørsmålOgSvarRespons = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = SPØRSMÅL_OG_SVAR_PATH,
                body = SpørsmålOgSvaralternativerRequest(
                    spørreundersøkelseId = spørreundersøkelseId.toString(),
                    sesjonsId = bliMedDTO.sesjonsId
                )
            )
            spørsmålOgSvarRespons.status shouldBe HttpStatusCode.OK
            val body = spørsmålOgSvarRespons.bodyAsText()
            val spørsmålOgSvaralternativer = Json.decodeFromString<List<SpørsmålOgSvaralternativerDTO>>(body)

            val spørsmål = spørsmålOgSvaralternativer.first()
            val svaralternativ = spørsmål.svaralternativer.first()

            val ukjentSpørsmålId = UUID.randomUUID()
            val svarRespons1 = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = SVAR_PATH,
                body = SvarRequest(
                    spørreundersøkelseId = spørreundersøkelseId.toString(),
                    sesjonsId = bliMedDTO.sesjonsId,
                    spørsmålId = ukjentSpørsmålId.toString(),
                    svarId = svaralternativ.id.toString(),
                )
            )
            svarRespons1.status shouldBe HttpStatusCode.Forbidden
            TestContainerHelper.fiaArbeidsgiverApi shouldContainLog "Ukjent spørsmål .$ukjentSpørsmålId.".toRegex()

            val ukjentSvarId = UUID.randomUUID()
            val svarRespons2 = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = SVAR_PATH,
                body = SvarRequest(
                    spørreundersøkelseId = spørreundersøkelseId.toString(),
                    sesjonsId = bliMedDTO.sesjonsId,
                    spørsmålId = spørsmål.id.toString(),
                    svarId = ukjentSvarId.toString(),
                )
            )
            svarRespons2.status shouldBe HttpStatusCode.Forbidden
            TestContainerHelper.fiaArbeidsgiverApi shouldContainLog "Ukjent svar .$ukjentSvarId.".toRegex()
        }
    }

    @Test
    fun `skal ikke kunne bli med på avsluttede spørreundersøkelser`() {
        val spørreundersøkelseId = UUID.randomUUID()
        TestContainerHelper.kafka.sendSpørreundersøkelse(
            spørreundersøkelseId = spørreundersøkelseId,
            spørreundersøkelsesStreng = TestContainerHelper.kafka.enStandardSpørreundersøkelse(
                spørreundersøkelseId = spørreundersøkelseId,
                spørreundersøkelseStatus = SpørreundersøkelseStatus.AVSLUTTET
            ).toJson()
        )

        runBlocking {
            val bliMedRespons = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = BLI_MED_PATH,
                body = BliMedRequest(spørreundersøkelseId = spørreundersøkelseId.toString())
            )
            bliMedRespons.status shouldBe HttpStatusCode.Forbidden
            TestContainerHelper.fiaArbeidsgiverApi shouldContainLog "Spørreundersøkelse med id '$spørreundersøkelseId'".toRegex()
        }
    }

    @Test
    fun `skal ikke kunne bli med på spørreundersøkelser som ikke er startet ennå`() {
        val spørreundersøkelseId = UUID.randomUUID()
        TestContainerHelper.kafka.sendSpørreundersøkelse(
            spørreundersøkelseId = spørreundersøkelseId,
            spørreundersøkelsesStreng = TestContainerHelper.kafka.enStandardSpørreundersøkelse(
                spørreundersøkelseId = spørreundersøkelseId,
                spørreundersøkelseStatus = SpørreundersøkelseStatus.OPPRETTET
            ).toJson()
        )

        runBlocking {
            val bliMedRespons = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = BLI_MED_PATH,
                body = BliMedRequest(spørreundersøkelseId = spørreundersøkelseId.toString())
            )
            bliMedRespons.status shouldBe HttpStatusCode.Forbidden
            TestContainerHelper.fiaArbeidsgiverApi shouldContainLog "Spørreundersøkelse med id '$spørreundersøkelseId'".toRegex()
        }
    }

    @Test
    fun `skal ikke kunne svare på avsluttede spørreundersøkelser`() {
        val spørreundersøkelseId = UUID.randomUUID()
        TestContainerHelper.kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId)

        runBlocking {
            val bliMedDTO = TestContainerHelper.fiaArbeidsgiverApi.bliMed(spørreundersøkelseId = spørreundersøkelseId)

            TestContainerHelper.kafka.sendSpørreundersøkelse(
                spørreundersøkelseId = spørreundersøkelseId,
                spørreundersøkelsesStreng = TestContainerHelper.kafka.enStandardSpørreundersøkelse(
                    spørreundersøkelseId = spørreundersøkelseId,
                    spørreundersøkelseStatus = SpørreundersøkelseStatus.AVSLUTTET
                ).toJson()
            )

            val spørsmålOgSvarRespons = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = SPØRSMÅL_OG_SVAR_PATH,
                body = SpørsmålOgSvaralternativerRequest(
                    spørreundersøkelseId = spørreundersøkelseId.toString(),
                    sesjonsId = bliMedDTO.sesjonsId
                )
            )
            spørsmålOgSvarRespons.status shouldBe HttpStatusCode.Forbidden
        }
    }

    @Test
    fun `vert skal få vite hvor mange som har blitt med`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val vertId = UUID.randomUUID()
        TestContainerHelper.kafka.enStandardSpørreundersøkelse(
            spørreundersøkelseId = spørreundersøkelseId,
            vertId = vertId
        )
            .also { spørreundersøkelse ->
                TestContainerHelper.kafka.sendSpørreundersøkelse(
                    spørreundersøkelseId = spørreundersøkelseId,
                    spørreundersøkelsesStreng = spørreundersøkelse.toJson()
                )
            }

        runBlocking {
            val antallDeltakere1 = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = VERT_ANTALL_DELTAKERE_PATH,
                body = VertshandlingRequest(
                    spørreundersøkelseId = spørreundersøkelseId.toString(),
                    vertId = vertId.toString()
                ),
            )
            Json.decodeFromString<AntallDeltakereDTO>(antallDeltakere1.bodyAsText()).antallDeltakere shouldBe 0

            TestContainerHelper.fiaArbeidsgiverApi.bliMed(spørreundersøkelseId = spørreundersøkelseId)
            val antallDeltakere2 = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = VERT_ANTALL_DELTAKERE_PATH,
                body = VertshandlingRequest(
                    spørreundersøkelseId = spørreundersøkelseId.toString(),
                    vertId = vertId.toString()
                )
            )
            Json.decodeFromString<AntallDeltakereDTO>(antallDeltakere2.bodyAsText()).antallDeltakere shouldBe 1
        }
    }

    @Test
    fun `vert skal ikke kunne inkrementere spørsmålindeks for en kategori som ikke er åpnet`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val vertId = UUID.randomUUID()

        TestContainerHelper.kafka.enStandardSpørreundersøkelse(
            spørreundersøkelseId = spørreundersøkelseId,
            vertId = vertId
        )
            .also { spørreundersøkelse ->
                TestContainerHelper.kafka.sendSpørreundersøkelse(
                    spørreundersøkelseId = spørreundersøkelseId,
                    spørreundersøkelsesStreng = spørreundersøkelse.toJson()
                )
            }

        runBlocking {

            val kategoristatus = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = VERT_KATEGORISTATUS_PATH,
                body = VertshandlingRequest(
                    spørreundersøkelseId = spørreundersøkelseId.toString(),
                    vertId = vertId.toString()
                ),
            )

            val kategoristatusBody = Json.decodeFromString<KategoristatusDTO>(kategoristatus.bodyAsText())

            kategoristatusBody.spørsmålindeks shouldBe null
            kategoristatusBody.status shouldBe OPPRETTET

            val startetKategori = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = VERT_INKREMENTER_SPØRSMÅL_PATH,
                body = VertshandlingRequest(
                    spørreundersøkelseId = spørreundersøkelseId.toString(),
                    vertId = vertId.toString()
                ),
            )

            startetKategori.status shouldBe HttpStatusCode.Conflict
        }
    }
    @Test
    fun `vert skal kunne hente gjeldende spørsmålindeks for en kategori og øke den`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val vertId = UUID.randomUUID()

        TestContainerHelper.kafka.enStandardSpørreundersøkelse(
            spørreundersøkelseId = spørreundersøkelseId,
            vertId = vertId
        )
            .also { spørreundersøkelse ->
                TestContainerHelper.kafka.sendSpørreundersøkelse(
                    spørreundersøkelseId = spørreundersøkelseId,
                    spørreundersøkelsesStreng = spørreundersøkelse.toJson()
                )
            }

        runBlocking {
            val kategoristatus = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = VERT_KATEGORISTATUS_PATH,
                body = VertshandlingRequest(
                    spørreundersøkelseId = spørreundersøkelseId.toString(),
                    vertId = vertId.toString()
                ),
            )

            val kategoristatusBody = Json.decodeFromString<KategoristatusDTO>(kategoristatus.bodyAsText())

            kategoristatusBody.spørsmålindeks shouldBe null
            kategoristatusBody.status shouldBe OPPRETTET

            TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = VERT_START_KATEGORI_PATH,
                body = StarteKategoriRequest(
                    spørreundersøkelseId = spørreundersøkelseId.toString(),
                    vertId = vertId.toString(),
                    kategori = PARTSSAMARBEID.name
                ),
            )

            val startetKategori = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = VERT_INKREMENTER_SPØRSMÅL_PATH,
                body = VertshandlingRequest(
                    spørreundersøkelseId = spørreundersøkelseId.toString(),
                    vertId = vertId.toString()
                ),
            )
            val startetKategoriBody = Json.decodeFromString<KategoristatusDTO>(startetKategori.bodyAsText())

            startetKategoriBody.spørsmålindeks shouldBe 0
            startetKategoriBody.status shouldBe KategoristatusDTO.Status.PÅBEGYNT
        }
    }

    @Test
    fun `deltager skal kunne hente neste spørsmål og få riktig status når vert har åpnet`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val vertId = UUID.randomUUID()

        val spørreundersøkelse = TestContainerHelper.kafka.enStandardSpørreundersøkelse(
            spørreundersøkelseId = spørreundersøkelseId,
            vertId = vertId
        )
        TestContainerHelper.kafka.sendSpørreundersøkelse(
            spørreundersøkelseId = spørreundersøkelseId,
            spørreundersøkelsesStreng = spørreundersøkelse.toJson()
        )

        runBlocking {
            val bliMedDTO = TestContainerHelper.fiaArbeidsgiverApi.bliMed(spørreundersøkelseId = spørreundersøkelseId)
            val spørsmålOgSvarRespons = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = SPØRSMÅL_OG_SVAR_PATH,
                body = SpørsmålOgSvaralternativerRequest(
                    spørreundersøkelseId = spørreundersøkelseId.toString(),
                    sesjonsId = bliMedDTO.sesjonsId
                )
            )
            val body = spørsmålOgSvarRespons.bodyAsText()
            val spørsmålOgSvaralternativer = Json.decodeFromString<List<SpørsmålOgSvaralternativerDTO>>(body)
            val idTilFørsteSpørsmål = spørsmålOgSvaralternativer.first().id

            val hvaErNesteSpørsmålRespons1 = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = NESTE_SPØRSMÅL_PATH,
                body = NesteSpørsmålRequest(
                    spørreundersøkelseId = spørreundersøkelseId.toString(),
                    sesjonsId = bliMedDTO.sesjonsId,
                    nåværrendeSpørsmålId = idTilFørsteSpørsmål.toString(),
                )
            )
            hvaErNesteSpørsmålRespons1.status shouldBe HttpStatusCode.OK
            val nesteSpørsmålDTO1 = Json.decodeFromString<NesteSpørsmålDTO>(hvaErNesteSpørsmålRespons1.bodyAsText())
            nesteSpørsmålDTO1.erNesteÅpnetAvVert shouldBe false

            TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = VERT_START_KATEGORI_PATH,
                body = StarteKategoriRequest(
                    spørreundersøkelseId = spørreundersøkelseId.toString(),
                    vertId = vertId.toString(),
                    kategori = PARTSSAMARBEID.name
                ),
            )

            TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = VERT_INKREMENTER_SPØRSMÅL_PATH,
                body = VertshandlingRequest(
                    spørreundersøkelseId = spørreundersøkelseId.toString(),
                    vertId = vertId.toString()
                ),
            )
            val startetKategori = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = VERT_INKREMENTER_SPØRSMÅL_PATH,
                body = VertshandlingRequest(
                    spørreundersøkelseId = spørreundersøkelseId.toString(),
                    vertId = vertId.toString()
                ),
            )
            val startetKategoriBody = Json.decodeFromString<KategoristatusDTO>(startetKategori.bodyAsText())

            startetKategoriBody.spørsmålindeks shouldBe 1

            val hvaErNesteSpørsmålRespons2 = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = NESTE_SPØRSMÅL_PATH,
                body = NesteSpørsmålRequest(
                    spørreundersøkelseId = spørreundersøkelseId.toString(),
                    sesjonsId = bliMedDTO.sesjonsId,
                    nåværrendeSpørsmålId = idTilFørsteSpørsmål.toString(),
                )
            )
            hvaErNesteSpørsmålRespons2.status shouldBe HttpStatusCode.OK
            val nesteSpørsmålDTO2 = Json.decodeFromString<NesteSpørsmålDTO>(hvaErNesteSpørsmålRespons2.bodyAsText())
            nesteSpørsmålDTO2.erNesteÅpnetAvVert shouldBe true
        }
    }

    @Test
    fun `deltaker skal kunne hente gjeldende spørsmålindeks`() {
        val spørreundersøkelseId = UUID.randomUUID()
        TestContainerHelper.kafka.sendSpørreundersøkelse(
            spørreundersøkelseId = spørreundersøkelseId,
        )

        runBlocking {
            val bliMedDTO = TestContainerHelper.fiaArbeidsgiverApi.bliMed(spørreundersøkelseId = spørreundersøkelseId)
            val spørsmålindeks = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = GJELDENDE_SPØRSMÅL_PATH,
                body = StatusRequest(
                    spørreundersøkelseId = spørreundersøkelseId.toString(),
                    sesjonsId = bliMedDTO.sesjonsId
                )
            )
            Json.decodeFromString<SpørsmålindeksDTO>(spørsmålindeks.bodyAsText()).indeks shouldBe 0
        }
    }
    @Test
    fun `deltaker skal kunne hente kategoristatus og ingen indeks for `() {
        val spørreundersøkelseId = UUID.randomUUID()
        TestContainerHelper.kafka.sendSpørreundersøkelse(
            spørreundersøkelseId = spørreundersøkelseId,
        )

        runBlocking {
            val bliMedDTO = TestContainerHelper.fiaArbeidsgiverApi.bliMed(spørreundersøkelseId = spørreundersøkelseId)

            val kategoristatus = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = KATEGORISTATUS_PATH,
                body = StatusRequest(
                    spørreundersøkelseId = spørreundersøkelseId.toString(),
                    sesjonsId = bliMedDTO.sesjonsId
                )
            )

            Json.decodeFromString<KategoristatusDTO>(kategoristatus.bodyAsText()).spørsmålindeks shouldBe null
            Json.decodeFromString<KategoristatusDTO>(kategoristatus.bodyAsText()).status shouldBe OPPRETTET
        }
    }

    @Test
    fun `skal returnere FORBIDDEN ved henting av neste spørsmål med ugyldig vertId`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val vertId = UUID.randomUUID()
        TestContainerHelper.kafka.enStandardSpørreundersøkelse(
            spørreundersøkelseId = spørreundersøkelseId,
            vertId = vertId
        )
            .also { spørreundersøkelse ->
                TestContainerHelper.kafka.sendSpørreundersøkelse(
                    spørreundersøkelseId = spørreundersøkelseId,
                    spørreundersøkelsesStreng = spørreundersøkelse.toJson()
                )
            }

        runBlocking {
            val response = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = VERT_INKREMENTER_SPØRSMÅL_PATH,
                body = VertshandlingRequest(
                    spørreundersøkelseId = spørreundersøkelseId.toString(),
                    vertId = UUID.randomUUID().toString()
                ),
            )
            response.status shouldBe HttpStatusCode.Forbidden
            TestContainerHelper.fiaArbeidsgiverApi shouldContainLog "Ugyldig vertId".toRegex()
        }
    }

    @Test
    fun `skal returnere FORBIDDEN ved henting av antall deltakere ugyldig vertId`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val vertId = UUID.randomUUID()
        TestContainerHelper.kafka.enStandardSpørreundersøkelse(
            spørreundersøkelseId = spørreundersøkelseId,
            vertId = vertId
        )
            .also { spørreundersøkelse ->
                TestContainerHelper.kafka.sendSpørreundersøkelse(
                    spørreundersøkelseId = spørreundersøkelseId,
                    spørreundersøkelsesStreng = spørreundersøkelse.toJson()
                )
            }

        runBlocking {
            val response = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = VERT_ANTALL_DELTAKERE_PATH,
                body = VertshandlingRequest(
                    spørreundersøkelseId = spørreundersøkelseId.toString(),
                    vertId = UUID.randomUUID().toString()
                ),
            )
            response.status shouldBe HttpStatusCode.Forbidden
            TestContainerHelper.fiaArbeidsgiverApi shouldContainLog "Ugyldig vertId".toRegex()
        }
    }

    @Test
    fun `skal returnere antallSvar`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val vertId = UUID.randomUUID()
        TestContainerHelper.kafka.enStandardSpørreundersøkelse(
            spørreundersøkelseId = spørreundersøkelseId,
            vertId = vertId
        )
            .also { spørreundersøkelse ->
                TestContainerHelper.kafka.sendSpørreundersøkelse(
                    spørreundersøkelseId = spørreundersøkelseId,
                    spørreundersøkelsesStreng = spørreundersøkelse.toJson()
                )
            }

        val UUIDLength = UUID.randomUUID().toString().length
        runBlocking {
            val antallDeltakere = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = VERT_ANTALL_DELTAKERE_PATH,
                body = VertshandlingRequest(
                    spørreundersøkelseId = spørreundersøkelseId.toString(),
                    vertId = vertId.toString()
                )
            )
            val antallDeltakereSvar = Json.decodeFromString<AntallDeltakereDTO>(antallDeltakere.bodyAsText())
            antallDeltakereSvar.antallSvar.forAll { svar ->
                svar.spørsmålId shouldHaveLength UUIDLength
                svar.antall shouldBe 2
            }
        }
    }
}

private fun Spørreundersøkelse.toJson() = Json.encodeToString(this)
