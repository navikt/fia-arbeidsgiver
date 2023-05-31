package no.nav.konfigurasjon

internal object Miljø {
    val tokenxClientId: String = System.getenv("TOKEN_X_CLIENT_ID")
    val tokenxIssuer: String = System.getenv("TOKEN_X_ISSUER")
    val tokenxJwkPath: String = System.getenv("TOKEN_X_JWKS_URI")
    val tokenxPrivateJwk: String = System.getenv("TOKEN_X_PRIVATE_JWK")
    val tokenXTokenEndpoint: String = System.getenv("TOKEN_X_TOKEN_ENDPOINT")
    val idportenIssuer: String = System.getenv("IDPORTEN_ISSUER")
    val idportenAudience: String = System.getenv("IDPORTEN_AUDIENCE")
    val idportenClientId: String = System.getenv("IDPORTEN_CLIENT_ID")
    val idportenJwkPath: String = System.getenv("IDPORTEN_JWKS_URI")
    val altinnProxyUrl: String = System.getenv("ALTINN_RETTIGHETER_PROXY_URL")
    val altinnRettigheterProxyClientId: String = System.getenv("ALTINN_RETTIGHETER_PROXY_CLIENT_ID")
}