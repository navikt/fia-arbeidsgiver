package no.nav.kafka

import no.nav.konfigurasjon.KafkaConfig.Companion.clientId

enum class Topic(
    val navn: String,
    private val prefix: String = "pia",
) {
    SPØRREUNDERSØKELSE_SVAR("sporreundersokelse-svar-v1"),
    SPØRREUNDERSØKELSE("sporreundersokelse-v1"),
    SAK_STATUS("ia-sak-status-v1");

    val konsumentGruppe
        get() = "${navn}_$clientId"

    val navnMedNamespace
        get() = "${prefix}.${navn}"
}
