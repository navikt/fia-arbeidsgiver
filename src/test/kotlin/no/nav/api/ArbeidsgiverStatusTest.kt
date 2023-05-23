package no.nav.api

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import no.nav.helper.TestContainerHelper
import no.nav.helper.performGet
import kotlin.test.Test

class ArbeidsgiverStatusTest {
    @Test
    fun `skal kunne nå isalive`() {
        runBlocking {
            TestContainerHelper.fiaArbeidsgiverApi.performGet("internal/isalive").status.value shouldBe 200
        }
    }

    @Test
    fun `skal få 401 (Unauthorized) dersom man går mot status uten innlogging`() {
        runBlocking {
            TestContainerHelper.fiaArbeidsgiverApi.performGet("status").status.value shouldBe 401
        }
    }
}
