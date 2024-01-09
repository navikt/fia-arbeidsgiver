package no.nav.konfigurasjon

import kotlin.time.Duration.Companion.seconds

class RateLimitKonfig {
    companion object {
        val limit = 5
        val refillPeriod = 3.seconds
    }
}