package no.nav.fia.arbeidsgiver.sporreundersokelse.api

import ia.felles.integrasjoner.kafkameldinger.spørreundersøkelse.SpørreundersøkelseStatus.AVSLUTTET
import ia.felles.integrasjoner.kafkameldinger.spørreundersøkelse.SpørreundersøkelseStatus.OPPRETTET
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldHaveLength
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import no.nav.fia.arbeidsgiver.helper.TestContainerHelper
import no.nav.fia.arbeidsgiver.helper.TestContainerHelper.Companion.applikasjon
import no.nav.fia.arbeidsgiver.helper.TestContainerHelper.Companion.shouldContainLog
import no.nav.fia.arbeidsgiver.helper.bliMed
import no.nav.fia.arbeidsgiver.helper.performPost
import no.nav.fia.arbeidsgiver.konfigurasjon.Topic
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.BliMedRequest
import org.junit.After
import org.junit.Before
import java.util.UUID
import kotlin.test.Test

class SpørreundersøkelseTest {
    private val topic = Topic.SPØRREUNDERSØKELSE_SVAR
    private val spørreundersøkelseSvarKonsument = TestContainerHelper.kafka.nyKonsument(consumerGroupId = topic.konsumentGruppe)

    @Before
    fun setUp() {
        spørreundersøkelseSvarKonsument.subscribe(mutableListOf(topic.navn))
    }

    @After
    fun tearDown() {
        spørreundersøkelseSvarKonsument.unsubscribe()
        spørreundersøkelseSvarKonsument.close()
    }

    @Test
    fun `skal kunne bli med i spørreundersøkelse`() {
        val spørreundersøkelseId = UUID.randomUUID()
        TestContainerHelper.kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId)

        runBlocking {
            val bliMedDTO = applikasjon.bliMed(spørreundersøkelseId = spørreundersøkelseId)
            bliMedDTO.spørreundersøkelseId shouldBe spørreundersøkelseId.toString()
            bliMedDTO.sesjonsId shouldHaveLength UUID.randomUUID().toString().length
        }
    }

    @Test
    fun `skal kunne bli med i spørreundersøkelse dersom ukjente felter`() { // Gir svar på om vi støtter ukjente felter i requestene våre
        val spørreundersøkelseId = UUID.randomUUID()
        TestContainerHelper.kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId)

        runBlocking {
            val response = applikasjon.performPost(
                url = BLI_MED_PATH,
                body = BliMedRequestMedUkjentFelt(
                    spørreundersøkelseId = spørreundersøkelseId.toString(),
                    ukjentFelt = "654321",
                ),
            )
            response.status shouldBe HttpStatusCode.OK
        }
    }

    @Suppress("unused")
    @Serializable
    private class BliMedRequestMedUkjentFelt(
        val spørreundersøkelseId: String,
        val ukjentFelt: String,
    )

    @Test
    fun `returnerer BAD_REQUEST dersom UUID er feil formatert`() {
        runBlocking {
            val response = applikasjon.performPost(
                url = BLI_MED_PATH,
                body = BliMedRequest(spørreundersøkelseId = "tullogtøys"),
            )
            response.status shouldBe HttpStatusCode.BadRequest
            applikasjon shouldContainLog "Ugyldig formatert UUID".toRegex()
            val body = response.bodyAsText()

            body shouldBe ""
        }
    }

    @Test
    fun `skal ikke kunne bli med på avsluttede spørreundersøkelser`() {
        val spørreundersøkelseId = UUID.randomUUID()
        TestContainerHelper.kafka.sendSpørreundersøkelse(
            spørreundersøkelseId = spørreundersøkelseId,
            spørreundersøkelse = TestContainerHelper.kafka.enStandardSpørreundersøkelse(
                id = spørreundersøkelseId,
                spørreundersøkelseStatus = AVSLUTTET,
            ),
        )

        runBlocking {
            val bliMedRespons = applikasjon.performPost(
                url = BLI_MED_PATH,
                body = BliMedRequest(spørreundersøkelseId = spørreundersøkelseId.toString()),
            )
            bliMedRespons.status shouldBe HttpStatusCode.Gone
            applikasjon shouldContainLog "Spørreundersøkelse med id '$spørreundersøkelseId'".toRegex()
        }
    }

    @Test
    fun `skal ikke kunne bli med på spørreundersøkelser som ikke er startet ennå`() {
        val spørreundersøkelseId = UUID.randomUUID()
        TestContainerHelper.kafka.sendSpørreundersøkelse(
            spørreundersøkelseId = spørreundersøkelseId,
            spørreundersøkelse = TestContainerHelper.kafka.enStandardSpørreundersøkelse(
                id = spørreundersøkelseId,
                spørreundersøkelseStatus = OPPRETTET,
            ),
        )

        runBlocking {
            val bliMedRespons = applikasjon.performPost(
                url = BLI_MED_PATH,
                body = BliMedRequest(spørreundersøkelseId = spørreundersøkelseId.toString()),
            )
            bliMedRespons.status shouldBe HttpStatusCode.Forbidden
            applikasjon shouldContainLog "Spørreundersøkelse med id '$spørreundersøkelseId'".toRegex()
        }
    }
}
