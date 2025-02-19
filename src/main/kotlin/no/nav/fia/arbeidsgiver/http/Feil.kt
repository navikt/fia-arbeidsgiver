package no.nav.fia.arbeidsgiver.http

import io.ktor.http.HttpStatusCode

class Feil(
    val feilmelding: String? = null,
    val opprinneligException: Throwable? = null,
    val feilkode: HttpStatusCode,
) : Throwable(feilmelding, opprinneligException)
