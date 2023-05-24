package no.nav

internal object Miljø {
    val tokenxClientId: String by lazy { System.getenv("TOKEN_X_CLIENT_ID") }
    val tokenxIssuer: String by lazy { System.getenv("TOKEN_X_ISSUER") }
    val tokenxJwkPath: String by lazy { System.getenv("TOKEN_X_JWKS_URI") }
    val tokenxPrivateJwk: String by lazy { System.getenv("TOKEN_X_PRIVATE_JWK") }
    val tokenXTokenEndpoint: String by lazy { System.getenv("TOKEN_X_TOKEN_ENDPOINT") }
    val altinnProxyUrl: String by lazy { System.getenv("ALTINN_RETTIGHETER_PROXY_URL") }
    val altinnRettigheterProxyClientId: String by lazy { System.getenv("ALTINN_RETTIGHETER_PROXY_CLIENT_ID") }
}