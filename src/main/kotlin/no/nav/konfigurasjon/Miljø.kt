package no.nav.konfigurasjon

enum class Cluster {
    `prod-gcp`, `dev-gcp`, lokal
}

internal object Miljø {
    val cluster = Cluster.valueOf(System.getenv("NAIS_CLUSTER_NAME") ?: "prod-gcp")

    val tokenxClientId: String = System.getenv("TOKEN_X_CLIENT_ID")
    val tokenxPrivateJwk: String = System.getenv("TOKEN_X_PRIVATE_JWK")
    val tokenXTokenEndpoint: String = System.getenv("TOKEN_X_TOKEN_ENDPOINT")
    val idportenIssuer: String = System.getenv("IDPORTEN_ISSUER")
    val idportenAudience: String = System.getenv("IDPORTEN_AUDIENCE")
    val idportenClientId: String = System.getenv("IDPORTEN_CLIENT_ID")
    val idportenJwkPath: String = System.getenv("IDPORTEN_JWKS_URI")
    val altinnProxyUrl: String = System.getenv("ALTINN_RETTIGHETER_PROXY_URL")
    val altinnRettigheterProxyClientId: String = System.getenv("ALTINN_RETTIGHETER_PROXY_CLIENT_ID")
}