package no.nav.fia.arbeidsgiver.konfigurasjon

import no.nav.fia.arbeidsgiver.konfigurasjon.KafkaConfig.Companion.CLIENT_ID

enum class KafkaTopics(
    val navn: String,
) {
    @Deprecated("Bruk SPØRREUNDERSØKELSE_HENDELSE")
    SPØRREUNDERSØKELSE_SVAR(
        navn = "pia.sporreundersokelse-svar-v1",
    ),
    SPØRREUNDERSØKELSE_HENDELSE(
        navn = "pia.sporreundersokelse-hendelse-v1",
    ),
    SPØRREUNDERSØKELSE_OPPDATERING(
        navn = "pia.sporreundersokelse-oppdatering-v1",
    ),
    SPØRREUNDERSØKELSE(
        navn = "pia.sporreundersokelse-v1",
    ),
    SAK_STATUS(
        navn = "pia.ia-sak-status-v1",
    ),
    ;

    val konsumentGruppe
        get() = "${this.navn}_$CLIENT_ID"
}
