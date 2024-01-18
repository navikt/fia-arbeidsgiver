package no.nav.kafka

import no.nav.konfigurasjon.KafkaConfig.Companion.clientId

enum class Topic(
    val navn: String,
    val prefix: String = "pia",
) {
    KARTLEGGING_SVAR("kartlegging-svar-v1"),
    KARTLEGGING_SPØRREUNDERSØKELSE("kartlegging-v1"),
    SAK_STATUS("ia-sak-status-v1");

    val konsumentGruppe
        get() = "${navn}_$clientId"

    val navnMedNamespace
        get() = "${prefix}.${navn}"
}
