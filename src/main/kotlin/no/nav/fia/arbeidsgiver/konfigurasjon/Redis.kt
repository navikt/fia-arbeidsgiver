package no.nav.fia.arbeidsgiver.konfigurasjon

class Redis {
    companion object {
        val redisUrl: String by lazy { System.getenv("REDIS_URI_FIA_SAMARBEIDSSTATUS") }
        val redisUsername: String by lazy { System.getenv("REDIS_USERNAME_FIA_SAMARBEIDSSTATUS") }
        val redisPassword: String by lazy { System.getenv("REDIS_PASSWORD_FIA_SAMARBEIDSSTATUS") }
    }
}
