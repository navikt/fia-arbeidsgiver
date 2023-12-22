package no.nav.konfigurasjon

class Redis {
    companion object {
        val redisUrl: String by lazy { System.getenv("REDIS_URI_STATUS") }
        val redisUsername: String by lazy { System.getenv("REDIS_USERNAME_STATUS") }
        val redisPassword: String by lazy { System.getenv("REDIS_PASSWORD_STATUS") }
    }
}
