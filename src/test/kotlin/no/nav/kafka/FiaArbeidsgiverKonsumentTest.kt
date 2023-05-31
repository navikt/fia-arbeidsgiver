package no.nav.kafka

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.toKotlinLocalDateTime
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import no.nav.helper.TestContainerHelper
import no.nav.helper.TestContainerHelper.Companion.shouldContainLog
import no.nav.konfigurasjon.Kafka
import no.nav.persistence.RedisService
import java.time.LocalDateTime
import kotlin.test.Test


class FiaArbeidsgiverKonsumentTest {
    @Test
    fun `skal kunne konsumere meldinger`() {
        val orgnr = "123456789"
        val iaStatusOppdatering = IASakStatus(
            orgnr = orgnr,
            saksnummer = "sak",
            status = "VURDERES",
            sistOppdatert = LocalDateTime.now().toKotlinLocalDateTime()
        )
        val somString = Json.encodeToString(iaStatusOppdatering)

        TestContainerHelper.kafka.sendOgVentTilKonsumert(
            orgnr,
            somString,
            "${Kafka.topicPrefix}.${Kafka.topic}",
            Kafka.consumerGroupId
        )
        TestContainerHelper.fiaArbeidsgiverApi shouldContainLog "Fikk melding om virksomhet .*$orgnr.*".toRegex()
        runBlocking {
            val result = RedisService(
                TestContainerHelper.redis.container.host,
                TestContainerHelper.redis.container.firstMappedPort,
                TestContainerHelper.redis.redisPassord
            ).henteSakStatus(orgnr)
            result.orgnr shouldBe orgnr
        }
    }

}
