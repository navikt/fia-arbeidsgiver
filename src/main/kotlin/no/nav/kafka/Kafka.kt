package no.nav.kafka

import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.config.SslConfigs

class Kafka {
    companion object {
        private val brokers: String = System.getenv("KAFKA_BROKERS")
        private val truststoreLocation: String = System.getenv("KAFKA_TRUSTSTORE_PATH")
        private val keystoreLocation: String = System.getenv("KAFKA_KEYSTORE_PATH")
        private val credstorePassword: String = System.getenv("KAFKA_CREDSTORE_PASSWORD")
        val topicPrefix: String = "pia"
        val topic: String = "ia-sak-status-v1"
        const val clientId: String = "fia-arbeidsgiver"
        const val consumerGroupId = "ia-sak-status_$clientId"

        private fun securityConfigs() =
            mapOf(
                CommonClientConfigs.SECURITY_PROTOCOL_CONFIG to "SSL",
                SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG to "",
                SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG to "JKS",
                SslConfigs.SSL_KEYSTORE_TYPE_CONFIG to "PKCS12",
                SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG to truststoreLocation,
                SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG to credstorePassword,
                SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG to keystoreLocation,
                SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG to credstorePassword,
                SslConfigs.SSL_KEY_PASSWORD_CONFIG to credstorePassword
            )

        fun consumerProperties() =
            baseConsumerProperties().apply {
                // TODO: Finn smidigere måte å få tester til å kjøre
                if (truststoreLocation.isBlank()) {
                    put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "PLAINTEXT")
                    put(SaslConfigs.SASL_MECHANISM, "PLAIN")
                } else {
                    putAll(securityConfigs())
                }
            }

        private fun baseConsumerProperties() =
            mapOf(
                CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG to brokers,
                ConsumerConfig.GROUP_ID_CONFIG to consumerGroupId,
                ConsumerConfig.CLIENT_ID_CONFIG to clientId,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
                ConsumerConfig.MAX_POLL_RECORDS_CONFIG to "1000",
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to "false"
            ).toProperties()
    }
}
