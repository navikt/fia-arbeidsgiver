package no.nav.fia.arbeidsgiver.konfigurasjon

import no.nav.fia.arbeidsgiver.konfigurasjon.KafkaConfig.Companion.clientId

enum class KafkaTopics(
    val navn: String,
    private val prefix: String = "pia",
) {
    SPØRREUNDERSØKELSE_SVAR("sporreundersokelse-svar-v1"),
    SPØRREUNDERSØKELSE_ANTALL_SVAR("sporreundersokelse-antall-svar-v1"),
    SPØRREUNDERSØKELSE("sporreundersokelse-v1"),
    SAK_STATUS("ia-sak-status-v1");

    val konsumentGruppe
        get() = "${navn}_$clientId"

    val navnMedNamespace
        get() = "${prefix}.${navn}"
}
