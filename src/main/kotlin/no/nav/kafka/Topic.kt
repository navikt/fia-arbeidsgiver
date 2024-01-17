package no.nav.kafka

enum class Topic(val navn: String, val prefix: String = "pia") {
    KARTLEGGING_SVAR("kartlegging-svar-topic-v1"),
    KARTLEGGING_SPØRREUNDERSØKELSE("kartlegging-topic-v1"),
    SAK_STATUS("ia-sak-status-v1");
    fun navnMedPrefix() = "${prefix}.${navn}"
}
