package no.nav.api

import io.ktor.http.*

class Feil(
    val feilmelding: String? = null,
    val opprinneligException: Throwable? = null,
    val feilkode: HttpStatusCode
): Throwable(feilmelding, opprinneligException)