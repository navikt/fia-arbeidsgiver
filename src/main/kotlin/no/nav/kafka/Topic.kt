package no.nav.kafka

enum class Topic(val navn: String, val prefix: String = "pia") {
    KARTLEGGING_SVAR("kartlegging-svar-v1"),
    KARTLEGGING_SPØRREUNDERSØKELSE("kartlegging-v1"),
    SAK_STATUS("ia-sak-status-v1");
    fun navnMedPrefix() = "${prefix}.${navn}"
}
