package no.nav.konfigurasjon

class Redis {
    companion object {
        val redisHost: String by lazy { System.getenv("REDIS_HOST") }
        val redisPort: Int by lazy { System.getenv("REDIS_PORT").toInt() }
    }
}
