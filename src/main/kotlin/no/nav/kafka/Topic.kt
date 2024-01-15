package no.nav.kafka

enum class Topic(val navn: String) {
    KARTLEGGING_SVAR("pia.kartlegging-svar-topic-v1"),
    KARTLEGGING_SPØRREUNDERSØKELSE("pia.kartlegging-topic-v1")
}
