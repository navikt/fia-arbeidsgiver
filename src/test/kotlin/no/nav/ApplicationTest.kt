package no.nav

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.server.testing.*
import kotlin.test.*
import io.ktor.http.*
import no.nav.plugins.*

class ApplicationTest {
    @Test
    fun testRoot() = testApplication {
        application {
            configureRouting()
        }
        client.get("/status").apply {
            assertEquals(HttpStatusCode.OK, status)
            assertEquals("Hei", bodyAsText())
        }
    }
}
