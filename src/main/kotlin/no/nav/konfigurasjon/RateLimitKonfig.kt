package no.nav.konfigurasjon

import kotlin.time.Duration.Companion.seconds

class RateLimitKonfig {
    companion object {
        val bliMedLimit = 5
        val generellLimit = 20
        val refillPeriod = 3.seconds
    }
}