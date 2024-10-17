package no.nav.fia.arbeidsgiver.metrics

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import no.nav.fia.arbeidsgiver.helper.TestContainerHelper.Companion.fiaArbeidsgiverApi
import no.nav.fia.arbeidsgiver.helper.performGet
import kotlin.test.Test

class MetricsTest {
    @Test
    fun `skal servere metrikker på metrics endepunkt`() {
        runBlocking {
            val metrikkRespons = fiaArbeidsgiverApi.performGet("/metrics")
            metrikkRespons.status shouldBe HttpStatusCode.OK
            metrikkRespons.bodyAsText() shouldContain "jvm_threads_daemon_threads"
        }
    }
}
