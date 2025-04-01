package no.nav.fia.arbeidsgiver.konfigurasjon

enum class Topic(
    val navn: String,
    val konsumentGruppe: String,
) {
    @Deprecated("Bruk SPØRREUNDERSØKELSE_HENDELSE")
    SPØRREUNDERSØKELSE_SVAR(
        navn = "pia.sporreundersokelse-svar-v1",
        konsumentGruppe = "fia-arbeidsgiver-sporreundersokelse-svar-producer",
    ),
    SPØRREUNDERSØKELSE_HENDELSE(
        navn = "pia.sporreundersokelse-hendelse-v1",
        konsumentGruppe = "fia-arbeidsgiver-sporreundersokelse-hendelse-producer",
    ),
    SPØRREUNDERSØKELSE_OPPDATERING(
        navn = "pia.sporreundersokelse-oppdatering-v1",
        konsumentGruppe = "fia-arbeidsgiver-sporreundersokelse-oppdatering-consumer",
    ),
    SPØRREUNDERSØKELSE(
        navn = "pia.sporreundersokelse-v1",
        konsumentGruppe = "fia-arbeidsgiver-sporreundersokelse-consumer",
    ),
    SAK_STATUS(
        navn = "pia.ia-sak-status-v1",
        konsumentGruppe = "fia-arbeidsgiver-ia-sak-status-consumer",
    ),
}
