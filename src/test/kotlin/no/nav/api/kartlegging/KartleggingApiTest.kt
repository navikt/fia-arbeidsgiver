package no.nav.api.kartlegging

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
import kotlin.time.toJavaDuration
import kotlinx.coroutines.time.delay
import kotlinx.serialization.Serializable
import no.nav.helper.TestContainerHelper.Companion.shouldContainLog
import no.nav.kafka.KartleggingSvar
import no.nav.kafka.Topic
import no.nav.konfigurasjon.RateLimitKonfig
import org.junit.After
import org.junit.Before

class KartleggingApiTest {
    private val kartleggingSvarKonsument = TestContainerHelper.kafka.nyKonsument(topic = Topic.KARTLEGGING_SVAR)

    @Before
    fun setUp() {
        kartleggingSvarKonsument.subscribe(mutableListOf(Topic.KARTLEGGING_SVAR.navn))
    }

    @After
    fun tearDown() {
        kartleggingSvarKonsument.unsubscribe()
        kartleggingSvarKonsument.close()
    }

    @Test
    fun `skal kunne starte kartlegging`() {
        val spørreundersøkelseId = UUID.randomUUID()
        TestContainerHelper.kafka.sendKartlegging(spørreundersøkelseId = spørreundersøkelseId)

        runBlocking {
            val response = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = BLI_MED_PATH,
                body = BliMedRequest(spørreundersøkelseId = spørreundersøkelseId.toString())
            )
            response.status shouldBe HttpStatusCode.OK
            val body = response.bodyAsText()
            val bliMedDTO = Json.decodeFromString<BliMedDTO>(body)
            bliMedDTO.id shouldBe spørreundersøkelseId.toString()
            bliMedDTO.sesjonsId shouldHaveLength UUID.randomUUID().toString().length
        }
    }

    @Test
    fun `skal kunne starte kartlegging dersom pin er med`() { //Gir svar på om vi støtter ukjente felter i requestene våre
        val spørreundersøkelseId = UUID.randomUUID()
        TestContainerHelper.kafka.sendKartlegging(spørreundersøkelseId = spørreundersøkelseId)

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
    fun `skal begrense antall forespørsler mot kartlegging-bli-med`() {

        runBlocking {
            delay(RateLimitKonfig.refillPeriod.toJavaDuration())
            repeat(RateLimitKonfig.bliMedLimit) {
                val response = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                    url = BLI_MED_PATH,
                    body = BliMedRequest(spørreundersøkelseId = UUID.randomUUID().toString())
                )
                response.status shouldBe HttpStatusCode.NotFound
            }
            val response = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = BLI_MED_PATH,
                body = BliMedRequest(spørreundersøkelseId = UUID.randomUUID().toString())
            )
            response.status shouldBe HttpStatusCode.TooManyRequests
            delay(RateLimitKonfig.refillPeriod.toJavaDuration())
        }
    }

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
    fun `skal kunne hente spørsmål og svar`() {
        val spørreundersøkelseId = UUID.randomUUID()
        TestContainerHelper.kafka.sendKartlegging(spørreundersøkelseId = spørreundersøkelseId)

        runBlocking {
            val bliMedRespons = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = BLI_MED_PATH,
                body = BliMedRequest(spørreundersøkelseId = spørreundersøkelseId.toString())
            )
            bliMedRespons.status shouldBe HttpStatusCode.OK
            val bliMedBody = bliMedRespons.bodyAsText()
            val bliMedDTO = Json.decodeFromString<BliMedDTO>(bliMedBody)

            val spørsmålOgSvarRespons = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = SPØRSMÅL_OG_SVAR_PATH,
                body = SpørsmålOgSvarRequest(spørreundersøkelseId = spørreundersøkelseId.toString(), sesjonsId = bliMedDTO.sesjonsId)
            )
            spørsmålOgSvarRespons.status shouldBe HttpStatusCode.OK
            val body = spørsmålOgSvarRespons.bodyAsText()
            val spørsmålOgSvaralternativer = Json.decodeFromString<List<SpørsmålOgSvaralternativerDTO>>(body)

            spørsmålOgSvaralternativer shouldHaveSize 1
            spørsmålOgSvaralternativer.first().svaralternativer shouldHaveSize 2
        }
    }

    @Test
    fun `skal ikke få spørsmål og svar dersom sesjonsId er ukjent`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val sesjonsId = UUID.randomUUID()
        TestContainerHelper.kafka.sendKartlegging(spørreundersøkelseId = spørreundersøkelseId)

        runBlocking {
            val bliMedRespons = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = BLI_MED_PATH,
                body = BliMedRequest(spørreundersøkelseId = spørreundersøkelseId.toString())
            )
            bliMedRespons.status shouldBe HttpStatusCode.OK

            val spørsmålOgSvarRespons = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = SPØRSMÅL_OG_SVAR_PATH,
                body = SpørsmålOgSvarRequest(spørreundersøkelseId = spørreundersøkelseId.toString(), sesjonsId = sesjonsId.toString())
            )
            spørsmålOgSvarRespons.status shouldBe HttpStatusCode.Forbidden
            TestContainerHelper.fiaArbeidsgiverApi shouldContainLog "Ugyldig sesjonsId".toRegex()
        }
    }

    @Test
    fun `skal kunne sende inn et gyldig svar`() {
        val spørreundersøkelseId = UUID.randomUUID()
        TestContainerHelper.kafka.sendKartlegging(spørreundersøkelseId = spørreundersøkelseId)

        runBlocking {
            val bliMedRespons = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = BLI_MED_PATH,
                body = BliMedRequest(spørreundersøkelseId = spørreundersøkelseId.toString())
            )
            bliMedRespons.status shouldBe HttpStatusCode.OK
            val bliMedBody = bliMedRespons.bodyAsText()
            val bliMedDTO = Json.decodeFromString<BliMedDTO>(bliMedBody)

            val spørsmålOgSvarRespons = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = SPØRSMÅL_OG_SVAR_PATH,
                body = SpørsmålOgSvarRequest(spørreundersøkelseId = spørreundersøkelseId.toString(), sesjonsId = bliMedDTO.sesjonsId)
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
                konsument = kartleggingSvarKonsument
            ) { meldinger ->
                val objektene = meldinger.map {
                    Json.decodeFromString<KartleggingSvar>(it)
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
    fun `skal få feilkode ved ukjent spørreundersøkelse`(){

        runBlocking {
            val svarRespons = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = SVAR_PATH,
                body = SvarRequest(
                    spørreundersøkelseId = UUID.randomUUID().toString(),
                    sesjonsId = UUID.randomUUID().toString(),
                    spørsmålId = UUID.randomUUID().toString(),
                    svarId = UUID.randomUUID().toString()
                )
            )
            svarRespons.status shouldBe HttpStatusCode.Forbidden
            TestContainerHelper.fiaArbeidsgiverApi shouldContainLog "Ukjent spørreundersøkelse ".toRegex()
        }
    }

    @Test
    fun `skal få feilkode ved ukjent svar og svaralternativ`() {
        val spørreundersøkelseId = UUID.randomUUID()
        TestContainerHelper.kafka.sendKartlegging(spørreundersøkelseId = spørreundersøkelseId)

        runBlocking {
            val bliMedRespons = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = BLI_MED_PATH,
                body = BliMedRequest(spørreundersøkelseId = spørreundersøkelseId.toString())
            )
            bliMedRespons.status shouldBe HttpStatusCode.OK
            val bliMedBody = bliMedRespons.bodyAsText()
            val bliMedDTO = Json.decodeFromString<BliMedDTO>(bliMedBody)

            val spørsmålOgSvarRespons = TestContainerHelper.fiaArbeidsgiverApi.performPost(
                url = SPØRSMÅL_OG_SVAR_PATH,
                body = SpørsmålOgSvarRequest(spørreundersøkelseId = spørreundersøkelseId.toString(), sesjonsId = bliMedDTO.sesjonsId)
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
                    svarId =  svaralternativ.id.toString(),
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
}
