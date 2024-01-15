package no.nav.konfigurasjon

import kotlin.time.Duration.Companion.seconds

private const val TEST_BLI_MED_LIMIT = 100
private const val TEST_GENERELL_LIMIT = 200

class RateLimitKonfig {
    fun getEnv() = mapOf(
        "BLI_MED_LIMIT" to "$TEST_BLI_MED_LIMIT",
        "GENERELL_LIMIT" to "$TEST_GENERELL_LIMIT",
    )

    companion object {
        val bliMedLimit: Int by lazy { System.getenv("BLI_MED_LIMIT")?.toInt() ?: TEST_BLI_MED_LIMIT }
        val generellLimit: Int by lazy { System.getenv("GENERELL_LIMIT")?.toInt() ?: TEST_GENERELL_LIMIT }
        val refillPeriod = 3.seconds
    }
}