package no.nav.fia.arbeidsgiver.konfigurasjon

enum class Cluster {
    `prod-gcp`, `dev-gcp`, lokal
}

internal object Miljø {
    val cluster = Cluster.valueOf(System.getenv("NAIS_CLUSTER_NAME") ?: "prod-gcp")

    //-- tokenX (idporten)
    val tokenxIssuer: String = System.getenv("TOKEN_X_ISSUER")
    val tokenxJwksUri: String = System.getenv("TOKEN_X_JWKS_URI")
    val tokenxClientId: String = System.getenv("TOKEN_X_CLIENT_ID")
    val tokenxPrivateJwk: String = System.getenv("TOKEN_X_PRIVATE_JWK")
    val tokenXTokenEndpoint: String = System.getenv("TOKEN_X_TOKEN_ENDPOINT")

    // -- OBO (azure)
    val azureIssuer: String = System.getenv("AZURE_OPENID_CONFIG_ISSUER")
    val azureJwksUri: String = System.getenv("AZURE_OPENID_CONFIG_JWKS_URI")
    val azureClientId: String = System.getenv("AZURE_APP_CLIENT_ID")
    // -- trenger ikke veksle token?
    // val azurePrivateJwk: String = System.getenv("AZURE_APP_JWK")
    // val azureTokenEndpoint: String = System.getenv("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT")
    // --
    val altinnProxyUrl: String = System.getenv("ALTINN_RETTIGHETER_PROXY_URL")
    val altinnRettigheterProxyClientId: String = System.getenv("ALTINN_RETTIGHETER_PROXY_CLIENT_ID")
}
