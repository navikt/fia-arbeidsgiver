package no.nav

internal object Miljø {
    val tokenxClientId: String = System.getenv("TOKEN_X_CLIENT_ID")
    val tokenxIssuer: String = System.getenv("TOKEN_X_ISSUER")
    val tokenxJwkPath: String = System.getenv("TOKEN_X_JWKS_URI")
    val tokenxPrivateJwk: String = System.getenv("TOKEN_X_PRIVATE_JWK")
    val tokenXTokenEndpoint: String = System.getenv("TOKEN_X_TOKEN_ENDPOINT")
    val altinnProxyUrl: String = System.getenv("ALTINN_RETTIGHETER_PROXY_URL")
    val altinnRettigheterProxyClientId: String = System.getenv("ALTINN_RETTIGHETER_PROXY_CLIENT_ID")
}