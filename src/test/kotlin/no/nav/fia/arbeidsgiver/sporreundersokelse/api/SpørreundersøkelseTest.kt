package no.nav.fia.arbeidsgiver.sporreundersokelse.api

import io.kotest.inspectors.forAll
import io.kotest.inspectors.forAtLeastOne
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldHaveLength
import io.ktor.client.call.body
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import no.nav.fia.arbeidsgiver.helper.TestContainerHelper
import no.nav.fia.arbeidsgiver.helper.TestContainerHelper.Companion.shouldContainLog
import no.nav.fia.arbeidsgiver.helper.bliMed
import no.nav.fia.arbeidsgiver.helper.nesteSpørsmål
import no.nav.fia.arbeidsgiver.helper.performPost
import no.nav.fia.arbeidsgiver.konfigurasjon.KafkaTopics
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.AntallDeltakereDTO
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.BliMedRequest
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.DeltakerhandlingRequest
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.NesteSpørsmålDTO
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.SpørsmålOgSvaralternativerTilFrontendDTO
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.StartTemaRequest
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.SvarRequest
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.TemastatusDTO
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.VertshandlingRequest
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.SpørreundersøkelseStatus
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.Tema
import no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.SpørreundersøkelseDto
import no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.SpørreundersøkelseSvarDTO
import org.junit.After
import org.junit.Before
import java.util.*
import kotlin.test.Test
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.AntallSvarPerSpørsmålDTO

class SpørreundersøkelseTest {
    private val spørreundersøkelseSvarKonsument =
        TestContainerHelper.kafka.nyKonsument(topic = KafkaTopics.SPØRREUNDERSØKELSE_SVAR)

    @Before
    fun setUp() {
        spørreundersøkelseSvarKonsument.subscribe(mutableListOf(KafkaTopics.SPØRREUNDERSØKELSE_SVAR.navnMedNamespace))
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
        val spørreundersøkelse = TestContainerHelper.kafka.enStandardSpørreundersøkelse(
            spørreundersøkelseId = spørreundersøkelseId,
        ).also { spørreundersøkelse ->
            TestContainerHelper.kafka.sendSpørreundersøkelse(
                spørreundersøkelseId = spørreundersøkelseId,
                spørreundersøkelsesStreng = spørreundersøkelse.toJson()
            )
        }

        val førsteSpørsmålId = spørreundersøkelse.temaMedSpørsmålOgSvaralternativer
            .first().spørsmålOgSvaralternativer
            .first().id

        runBlocking {
            val bliMedDTO = TestContainerHelper.fiaArbeidsgiverApi.bliMed(spørreundersøkelseId = spørreundersøkelseId)

            val spørsmålOgSvarRespons = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = "$SPØRSMÅL_OG_SVAR_PATH/$førsteSpørsmålId",
                body = DeltakerhandlingRequest(
                    spørreundersøkelseId = spørreundersøkelseId.toString(),
                    sesjonsId = bliMedDTO.sesjonsId
                )
            )
            spørsmålOgSvarRespons.status shouldBe HttpStatusCode.OK
            val body = spørsmålOgSvarRespons.bodyAsText()
            val spørsmålOgSvaralternativer = Json.decodeFromString<SpørsmålOgSvaralternativerTilFrontendDTO>(body)

            spørsmålOgSvaralternativer.id.toString() shouldBe førsteSpørsmålId
            spørsmålOgSvaralternativer.svaralternativer shouldHaveSize 2
        }
    }

    @Test
    fun `deltaker skal kunne hente neste spørsmålId`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val spørreundersøkelse = TestContainerHelper.kafka.enStandardSpørreundersøkelse(
            spørreundersøkelseId = spørreundersøkelseId,
        ).also { spørreundersøkelse ->
            TestContainerHelper.kafka.sendSpørreundersøkelse(
                spørreundersøkelseId = spørreundersøkelseId,
                spørreundersøkelsesStreng = spørreundersøkelse.toJson()
            )
        }

        val idTilFørsteSpørsmål =
            spørreundersøkelse.temaMedSpørsmålOgSvaralternativer.first().spørsmålOgSvaralternativer[0].id
        val idTilAndreSpørsmål =
            spørreundersøkelse.temaMedSpørsmålOgSvaralternativer.first().spørsmålOgSvaralternativer[1].id
        val idTilFørsteSpørsmålForNyttTema =
            spørreundersøkelse.temaMedSpørsmålOgSvaralternativer[1].spørsmålOgSvaralternativer[0].id

        runBlocking {
            val bliMedDTO = TestContainerHelper.fiaArbeidsgiverApi.bliMed(spørreundersøkelseId = spørreundersøkelseId)
            val nesteSpørsmålFraStart = TestContainerHelper.fiaArbeidsgiverApi.nesteSpørsmål(
                bliMedDTO = bliMedDTO,
                nåværendeSpørsmålId = "START"
            )

            nesteSpørsmålFraStart.nesteSpørsmålId shouldBe idTilFørsteSpørsmål
            nesteSpørsmålFraStart.forrigeSpørsmålId shouldBe null
            nesteSpørsmålFraStart.erNesteÅpnetAvVert shouldBe false

            val nesteSpørsmålFraFørsteSpørsmål = TestContainerHelper.fiaArbeidsgiverApi.nesteSpørsmål(
                bliMedDTO = bliMedDTO,
                nåværendeSpørsmålId = idTilFørsteSpørsmål
            )

            nesteSpørsmålFraFørsteSpørsmål.nesteSpørsmålId shouldBe idTilAndreSpørsmål
            nesteSpørsmålFraFørsteSpørsmål.forrigeSpørsmålId shouldBe null
            nesteSpørsmålFraFørsteSpørsmål.erNesteÅpnetAvVert shouldBe false

            val nesteSpørsmålFraAndreSpørsmål = TestContainerHelper.fiaArbeidsgiverApi.nesteSpørsmål(
                bliMedDTO = bliMedDTO,
                nåværendeSpørsmålId = idTilAndreSpørsmål
            )

            nesteSpørsmålFraAndreSpørsmål.nesteSpørsmålId shouldBe idTilFørsteSpørsmålForNyttTema
            nesteSpørsmålFraAndreSpørsmål.forrigeSpørsmålId shouldBe idTilFørsteSpørsmål
            nesteSpørsmålFraAndreSpørsmål.erNesteÅpnetAvVert shouldBe false
        }
    }

    @Test
    fun `vert skal kunne hente neste spørsmålId`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val vertId = UUID.randomUUID()
        val spørreundersøkelse = TestContainerHelper.kafka.enStandardSpørreundersøkelse(
            spørreundersøkelseId = spørreundersøkelseId,
            vertId = vertId,
        ).also { spørreundersøkelse ->
            TestContainerHelper.kafka.sendSpørreundersøkelse(
                spørreundersøkelseId = spørreundersøkelseId,
                spørreundersøkelsesStreng = spørreundersøkelse.toJson()
            )
        }

        val idTilFørsteSpørsmål =
            spørreundersøkelse.temaMedSpørsmålOgSvaralternativer.first().spørsmålOgSvaralternativer[0].id
        val idTilAndreSpørsmål =
            spørreundersøkelse.temaMedSpørsmålOgSvaralternativer.first().spørsmålOgSvaralternativer[1].id
        val idTilFørsteSpørsmålForNyttTema =
            spørreundersøkelse.temaMedSpørsmålOgSvaralternativer[1].spørsmålOgSvaralternativer[0].id

        runBlocking {
            val nesteSpørsmålFraStart = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = "$VERT_NESTE_SPØRSMÅL_PATH/START",
                body = VertshandlingRequest(
                    spørreundersøkelseId = spørreundersøkelseId.toString(),
                    vertId = vertId.toString(),
                )
            ).body<NesteSpørsmålDTO>()

            nesteSpørsmålFraStart.nesteSpørsmålId shouldBe idTilFørsteSpørsmål
            nesteSpørsmålFraStart.forrigeSpørsmålId shouldBe null
            nesteSpørsmålFraStart.erNesteÅpnetAvVert shouldBe false

            val nesteSpørsmålFraFørsteSpørsmål = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = "$VERT_NESTE_SPØRSMÅL_PATH/$idTilFørsteSpørsmål",
                body = VertshandlingRequest(
                    spørreundersøkelseId = spørreundersøkelseId.toString(),
                    vertId = vertId.toString(),
                )
            ).body<NesteSpørsmålDTO>()

            nesteSpørsmålFraFørsteSpørsmål.nesteSpørsmålId shouldBe idTilAndreSpørsmål
            nesteSpørsmålFraFørsteSpørsmål.forrigeSpørsmålId shouldBe null
            nesteSpørsmålFraFørsteSpørsmål.erNesteÅpnetAvVert shouldBe false

            val nesteSpørsmålFraAndreSpørsmål = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = "$VERT_NESTE_SPØRSMÅL_PATH/$idTilAndreSpørsmål",
                body = VertshandlingRequest(
                    spørreundersøkelseId = spørreundersøkelseId.toString(),
                    vertId = vertId.toString(),
                )
            ).body<NesteSpørsmålDTO>()

            nesteSpørsmålFraAndreSpørsmål.nesteSpørsmålId shouldBe idTilFørsteSpørsmålForNyttTema
            nesteSpørsmålFraAndreSpørsmål.forrigeSpørsmålId shouldBe idTilFørsteSpørsmål
            nesteSpørsmålFraAndreSpørsmål.erNesteÅpnetAvVert shouldBe false
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

            val førsteSpørsmål =
                spørreundersøkelse.temaMedSpørsmålOgSvaralternativer.first().spørsmålOgSvaralternativer.first()

            val spørsmålOgSvarRespons = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = "$SPØRSMÅL_OG_SVAR_PATH/${førsteSpørsmål.id}",
                body = DeltakerhandlingRequest(
                    spørreundersøkelseId = spørreundersøkelseId.toString(),
                    sesjonsId = bliMedDTO.sesjonsId
                )
            )
            spørsmålOgSvarRespons.status shouldBe HttpStatusCode.OK
            val body = spørsmålOgSvarRespons.bodyAsText()
            val spørsmålOgSvaralternativer = Json.decodeFromString<SpørsmålOgSvaralternativerTilFrontendDTO>(body)
            
            spørsmålOgSvaralternativer.id shouldBe UUID.fromString(førsteSpørsmål.id)
            spørsmålOgSvaralternativer.spørsmålIndeks shouldBe 0
            spørsmålOgSvaralternativer.sisteSpørsmålIndeks shouldBe 3
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
                body = DeltakerhandlingRequest(
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
        val spørreundersøkelse = TestContainerHelper.kafka.enStandardSpørreundersøkelse(
            spørreundersøkelseId = spørreundersøkelseId,
            vertId = vertId
        ).also { spørreundersøkelse ->
            TestContainerHelper.kafka.sendSpørreundersøkelse(
                spørreundersøkelseId = spørreundersøkelseId,
                spørreundersøkelsesStreng = spørreundersøkelse.toJson()
            )
        }

        val førsteSpørsmålId =
            spørreundersøkelse.temaMedSpørsmålOgSvaralternativer.first().spørsmålOgSvaralternativer.first().id


        runBlocking {
            val spørsmålOgSvarRespons = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = "$VERT_SPØRSMÅL_OG_SVAR_PATH/$førsteSpørsmålId",
                body = VertshandlingRequest(
                    spørreundersøkelseId = spørreundersøkelseId.toString(),
                    vertId = vertId.toString()
                )
            )

            spørsmålOgSvarRespons.status shouldBe HttpStatusCode.OK
            val body = spørsmålOgSvarRespons.bodyAsText()
            val spørsmålOgSvaralternativer = Json.decodeFromString<SpørsmålOgSvaralternativerTilFrontendDTO>(body)

            spørsmålOgSvaralternativer.id.toString() shouldBe førsteSpørsmålId
            spørsmålOgSvaralternativer.svaralternativer shouldHaveSize 2
        }
    }

    @Test
    fun `skal ikke få spørsmål og svar dersom sesjonsId er ukjent`() {
        val sesjonsId = UUID.randomUUID()
        val spørreundersøkelseId = UUID.randomUUID()
        val spørreundersøkelse = TestContainerHelper.kafka.enStandardSpørreundersøkelse(
            spørreundersøkelseId = spørreundersøkelseId,
        ).also { spørreundersøkelse ->
            TestContainerHelper.kafka.sendSpørreundersøkelse(
                spørreundersøkelseId = spørreundersøkelseId,
                spørreundersøkelsesStreng = spørreundersøkelse.toJson()
            )
        }

        val førsteSpørsmålId = spørreundersøkelse.temaMedSpørsmålOgSvaralternativer
            .first().spørsmålOgSvaralternativer
            .first().id

        runBlocking {
            val spørsmålOgSvarRespons = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = "$SPØRSMÅL_OG_SVAR_PATH/$førsteSpørsmålId",
                body = DeltakerhandlingRequest(
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
        val spørreundersøkelse = TestContainerHelper.kafka.enStandardSpørreundersøkelse(
            spørreundersøkelseId = spørreundersøkelseId,
        ).also { spørreundersøkelse ->
            TestContainerHelper.kafka.sendSpørreundersøkelse(
                spørreundersøkelseId = spørreundersøkelseId,
                spørreundersøkelsesStreng = spørreundersøkelse.toJson()
            )
        }

        val førsteSpørsmål = spørreundersøkelse.temaMedSpørsmålOgSvaralternativer
            .first().spørsmålOgSvaralternativer
            .first()
        val førsteSvaralternativ = spørreundersøkelse.temaMedSpørsmålOgSvaralternativer
            .first().spørsmålOgSvaralternativer
            .first().svaralternativer
            .first()

        runBlocking {
            val bliMedDTO = TestContainerHelper.fiaArbeidsgiverApi.bliMed(spørreundersøkelseId = spørreundersøkelseId)

            val sendSvarRespons = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = SVAR_PATH,
                body = SvarRequest(
                    spørreundersøkelseId = spørreundersøkelseId.toString(),
                    sesjonsId = bliMedDTO.sesjonsId,
                    spørsmålId = førsteSpørsmål.id,
                    svarId = førsteSvaralternativ.svarId
                )
            )
            sendSvarRespons.status shouldBe HttpStatusCode.OK

            TestContainerHelper.kafka.ventOgKonsumerKafkaMeldinger(
                key = "${bliMedDTO.sesjonsId}_${førsteSpørsmål.id}",
                konsument = spørreundersøkelseSvarKonsument
            ) { meldinger ->
                val objektene = meldinger.map {
                    Json.decodeFromString<SpørreundersøkelseSvarDTO>(it)
                }
                objektene shouldHaveAtLeastSize 1
                objektene.forAtLeastOne {
                    it.spørreundersøkelseId shouldBe spørreundersøkelseId.toString()
                    it.sesjonId shouldBe bliMedDTO.sesjonsId
                    it.spørsmålId shouldBe førsteSpørsmål.id
                    it.svarId shouldBe førsteSvaralternativ.svarId
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
        val spørreundersøkelse = TestContainerHelper.kafka.enStandardSpørreundersøkelse(
            spørreundersøkelseId = spørreundersøkelseId,
        ).also { spørreundersøkelse ->
            TestContainerHelper.kafka.sendSpørreundersøkelse(
                spørreundersøkelseId = spørreundersøkelseId,
                spørreundersøkelsesStreng = spørreundersøkelse.toJson()
            )
        }

        val førsteSpørsmål = spørreundersøkelse.temaMedSpørsmålOgSvaralternativer
            .first().spørsmålOgSvaralternativer
            .first()

        val førsteSvar = førsteSpørsmål.svaralternativer.first()

        runBlocking {
            val bliMedDTO = TestContainerHelper.fiaArbeidsgiverApi.bliMed(spørreundersøkelseId = spørreundersøkelseId)

            val ukjentSpørsmålId = UUID.randomUUID()

            val svarRespons1 = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = SVAR_PATH,
                body = SvarRequest(
                    spørreundersøkelseId = spørreundersøkelseId.toString(),
                    sesjonsId = bliMedDTO.sesjonsId,
                    spørsmålId = ukjentSpørsmålId.toString(),
                    svarId = førsteSvar.svarId,
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
                    spørsmålId = førsteSpørsmål.id,
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
        val spørreundersøkelse = TestContainerHelper.kafka.enStandardSpørreundersøkelse(
            spørreundersøkelseId = spørreundersøkelseId,
        ).also { spørreundersøkelse ->
            TestContainerHelper.kafka.sendSpørreundersøkelse(
                spørreundersøkelseId = spørreundersøkelseId,
                spørreundersøkelsesStreng = spørreundersøkelse.toJson()
            )
        }

        val førsteSpørsmålId =
            spørreundersøkelse.temaMedSpørsmålOgSvaralternativer.first().spørsmålOgSvaralternativer.first().id

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
                url = "$SPØRSMÅL_OG_SVAR_PATH/$førsteSpørsmålId",
                body = DeltakerhandlingRequest(
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
    fun `vert skal ikke kunne inkrementere spørsmålindeks for ett tema som ikke er åpnet`() {
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

            val temastatus = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = VERT_TEMA_PATH,
                body = VertshandlingRequest(
                    spørreundersøkelseId = spørreundersøkelseId.toString(),
                    vertId = vertId.toString()
                ),
            )

            val temastatusBody = Json.decodeFromString<TemastatusDTO>(temastatus.bodyAsText())

            temastatusBody.spørsmålindeks shouldBe -1
            temastatusBody.antallSpørsmål shouldBe 2
            temastatusBody.status shouldBe TemastatusDTO.Status.OPPRETTET

            val startetTema = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = VERT_INKREMENTER_SPØRSMÅL_PATH,
                body = VertshandlingRequest(
                    spørreundersøkelseId = spørreundersøkelseId.toString(),
                    vertId = vertId.toString()
                ),
            )

            startetTema.status shouldBe HttpStatusCode.Conflict
        }
    }

    @Test
    fun `vert skal kunne hente gjeldende spørsmålindeks for ett tema og øke den`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val vertId = UUID.randomUUID()
        TestContainerHelper.kafka.enStandardSpørreundersøkelse(
            spørreundersøkelseId = spørreundersøkelseId,
            vertId = vertId
        ).also { spørreundersøkelse ->
            TestContainerHelper.kafka.sendSpørreundersøkelse(
                spørreundersøkelseId = spørreundersøkelseId,
                spørreundersøkelsesStreng = spørreundersøkelse.toJson()
            )
        }

        runBlocking {
            val temastatus = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = VERT_TEMA_PATH,
                body = VertshandlingRequest(
                    spørreundersøkelseId = spørreundersøkelseId.toString(),
                    vertId = vertId.toString()
                ),
            )

            val temastatusBody = Json.decodeFromString<TemastatusDTO>(temastatus.bodyAsText())

            temastatusBody.spørsmålindeks shouldBe -1
            temastatusBody.status shouldBe TemastatusDTO.Status.OPPRETTET
            temastatusBody.antallSpørsmål shouldBe 2

            TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = VERT_START_TEMA_PATH,
                body = StartTemaRequest(
                    spørreundersøkelseId = spørreundersøkelseId.toString(),
                    vertId = vertId.toString(),
                    tema = Tema.UTVIKLE_PARTSSAMARBEID
                ),
            )

            val startetTema = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = VERT_INKREMENTER_SPØRSMÅL_PATH,
                body = VertshandlingRequest(
                    spørreundersøkelseId = spørreundersøkelseId.toString(),
                    vertId = vertId.toString()
                ),
            )
            val startetTemaBody = Json.decodeFromString<TemastatusDTO>(startetTema.bodyAsText())

            startetTemaBody.spørsmålindeks shouldBe 0
            startetTemaBody.antallSpørsmål shouldBe 2
            startetTemaBody.status shouldBe TemastatusDTO.Status.PÅBEGYNT
        }
    }

    @Test
    fun `deltager skal kunne hente neste spørsmål og få riktig status når vert har åpnet`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val vertId = UUID.randomUUID()

        val spørreundersøkelse = TestContainerHelper.kafka.enStandardSpørreundersøkelse(
            spørreundersøkelseId = spørreundersøkelseId,
            vertId = vertId
        ).also { spørreundersøkelse ->
            TestContainerHelper.kafka.sendSpørreundersøkelse(
                spørreundersøkelseId = spørreundersøkelseId,
                spørreundersøkelsesStreng = spørreundersøkelse.toJson()
            )
        }
        val førsteSpørsmålId =
            spørreundersøkelse.temaMedSpørsmålOgSvaralternativer.first().spørsmålOgSvaralternativer.first().id


        runBlocking {
            val bliMedDTO = TestContainerHelper.fiaArbeidsgiverApi.bliMed(spørreundersøkelseId = spørreundersøkelseId)

            val hvaErNesteSpørsmålRespons1 = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = "$NESTE_SPØRSMÅL_PATH/$førsteSpørsmålId",
                body = DeltakerhandlingRequest(
                    spørreundersøkelseId = spørreundersøkelseId.toString(),
                    sesjonsId = bliMedDTO.sesjonsId,
                )
            )
            hvaErNesteSpørsmålRespons1.status shouldBe HttpStatusCode.OK
            val nesteSpørsmålDTO1 = Json.decodeFromString<NesteSpørsmålDTO>(hvaErNesteSpørsmålRespons1.bodyAsText())
            nesteSpørsmålDTO1.erNesteÅpnetAvVert shouldBe false

            TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = VERT_START_TEMA_PATH,
                body = StartTemaRequest(
                    spørreundersøkelseId = spørreundersøkelseId.toString(),
                    vertId = vertId.toString(),
                    tema = Tema.UTVIKLE_PARTSSAMARBEID
                ),
            )

            TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = VERT_INKREMENTER_SPØRSMÅL_PATH,
                body = VertshandlingRequest(
                    spørreundersøkelseId = spørreundersøkelseId.toString(),
                    vertId = vertId.toString()
                ),
            )
            val startetTema = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = VERT_INKREMENTER_SPØRSMÅL_PATH,
                body = VertshandlingRequest(
                    spørreundersøkelseId = spørreundersøkelseId.toString(),
                    vertId = vertId.toString()
                ),
            )
            val startetTemaBody = Json.decodeFromString<TemastatusDTO>(startetTema.bodyAsText())

            startetTemaBody.spørsmålindeks shouldBe 1
            startetTemaBody.antallSpørsmål shouldBe 2

            val hvaErNesteSpørsmålRespons2 = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = "$NESTE_SPØRSMÅL_PATH/$førsteSpørsmålId",
                body = DeltakerhandlingRequest(
                    spørreundersøkelseId = spørreundersøkelseId.toString(),
                    sesjonsId = bliMedDTO.sesjonsId,
                )
            )
            hvaErNesteSpørsmålRespons2.status shouldBe HttpStatusCode.OK
            val nesteSpørsmålDTO2 = Json.decodeFromString<NesteSpørsmålDTO>(hvaErNesteSpørsmålRespons2.bodyAsText())
            nesteSpørsmålDTO2.erNesteÅpnetAvVert shouldBe true
        }
    }

    @Test
    fun `deltaker skal kunne hente temastatus og ingen indeks for `() {
        val spørreundersøkelseId = UUID.randomUUID()
        TestContainerHelper.kafka.sendSpørreundersøkelse(
            spørreundersøkelseId = spørreundersøkelseId,
        )

        runBlocking {
            val bliMedDTO = TestContainerHelper.fiaArbeidsgiverApi.bliMed(spørreundersøkelseId = spørreundersøkelseId)

            val temastatusResponse = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = TEMASTATUS_PATH,
                body = DeltakerhandlingRequest(
                    spørreundersøkelseId = spørreundersøkelseId.toString(),
                    sesjonsId = bliMedDTO.sesjonsId
                )
            )

            val temastatus = Json.decodeFromString<TemastatusDTO>(temastatusResponse.bodyAsText())
            temastatus.spørsmålindeks shouldBe -1
            temastatus.antallSpørsmål shouldBe 2
            temastatus.status shouldBe TemastatusDTO.Status.OPPRETTET
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
        val spørreundersøkelse = TestContainerHelper.kafka.enStandardSpørreundersøkelse(
            spørreundersøkelseId = spørreundersøkelseId,
            vertId = vertId
        )
        TestContainerHelper.kafka.sendSpørreundersøkelse(
            spørreundersøkelseId = spørreundersøkelseId,
            spørreundersøkelsesStreng = spørreundersøkelse.toJson()
        )

        spørreundersøkelse.temaMedSpørsmålOgSvaralternativer.forEach { tema ->
            tema.spørsmålOgSvaralternativer.forEach {
                TestContainerHelper.kafka.sendAntallSvar(
                    spørreundersøkelseId = spørreundersøkelseId.toString(),
                    spørsmålId = it.id,
                    antallSvar = 2
                )
            }
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


    @Test
    fun `vert skal kunne hente ut antall svar og antall deltakere per spørsmål`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val vertId = UUID.randomUUID()
        val spørreundersøkelse = TestContainerHelper.kafka.enStandardSpørreundersøkelse(
            spørreundersøkelseId = spørreundersøkelseId,
            vertId = vertId
        ).also { spørreundersøkelse ->
            TestContainerHelper.kafka.sendSpørreundersøkelse(
                spørreundersøkelseId = spørreundersøkelseId,
                spørreundersøkelsesStreng = spørreundersøkelse.toJson()
            )
        }

        spørreundersøkelse.temaMedSpørsmålOgSvaralternativer.first().also { tema ->
            tema.spørsmålOgSvaralternativer.first().also {
                TestContainerHelper.kafka.sendAntallSvar(
                    spørreundersøkelseId = spørreundersøkelseId.toString(),
                    spørsmålId = it.id,
                    antallSvar = 2
                )
            }
        }


        val førsteSpørsmålId = spørreundersøkelse.temaMedSpørsmålOgSvaralternativer[0].spørsmålOgSvaralternativer[0].id
        val andreSpørsmålId = spørreundersøkelse.temaMedSpørsmålOgSvaralternativer[0].spørsmålOgSvaralternativer[1].id

        runBlocking {
            TestContainerHelper.fiaArbeidsgiverApi.bliMed(spørreundersøkelseId = spørreundersøkelseId)
            TestContainerHelper.fiaArbeidsgiverApi.bliMed(spørreundersøkelseId = spørreundersøkelseId)
            TestContainerHelper.fiaArbeidsgiverApi.bliMed(spørreundersøkelseId = spørreundersøkelseId)

            val antallDeltakere1 = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = "$VERT_ANTALL_SVAR_PATH/$førsteSpørsmålId",
                body = VertshandlingRequest(
                    spørreundersøkelseId = spørreundersøkelseId.toString(),
                    vertId = vertId.toString()
                )
            )

            val antallSvar1 = Json.decodeFromString<AntallSvarPerSpørsmålDTO>(antallDeltakere1.bodyAsText())

            antallSvar1.antallSvar shouldBe 2
            antallSvar1.antallDeltakere shouldBe 3

            TestContainerHelper.fiaArbeidsgiverApi.bliMed(spørreundersøkelseId = spørreundersøkelseId)

            val antallDeltakere2 = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = "$VERT_ANTALL_SVAR_PATH/$andreSpørsmålId",
                body = VertshandlingRequest(
                    spørreundersøkelseId = spørreundersøkelseId.toString(),
                    vertId = vertId.toString()
                )
            )

            val antallSvar2 = Json.decodeFromString<AntallSvarPerSpørsmålDTO>(antallDeltakere2.bodyAsText())
            antallSvar2.antallSvar shouldBe 0
            antallSvar2.antallDeltakere shouldBe 4
        }

    }

    private fun SpørreundersøkelseDto.toJson() = Json.encodeToString(this)
}