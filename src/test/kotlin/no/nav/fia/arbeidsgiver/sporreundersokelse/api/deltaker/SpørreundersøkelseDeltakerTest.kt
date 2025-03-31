package no.nav.fia.arbeidsgiver.sporreundersokelse.api.deltaker

import ia.felles.integrasjoner.kafkameldinger.spørreundersøkelse.SpørreundersøkelseStatus.AVSLUTTET
import ia.felles.integrasjoner.kafkameldinger.spørreundersøkelse.SpørreundersøkelseStatus.OPPRETTET
import io.kotest.assertions.shouldFail
import io.kotest.inspectors.forAtLeastOne
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.request.header
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import no.nav.fia.arbeidsgiver.helper.TestContainerHelper.Companion.applikasjon
import no.nav.fia.arbeidsgiver.helper.TestContainerHelper.Companion.kafka
import no.nav.fia.arbeidsgiver.helper.TestContainerHelper.Companion.shouldContainLog
import no.nav.fia.arbeidsgiver.helper.bliMed
import no.nav.fia.arbeidsgiver.helper.hentFørsteSpørsmål
import no.nav.fia.arbeidsgiver.helper.hentSpørsmålSomDeltaker
import no.nav.fia.arbeidsgiver.helper.performGet
import no.nav.fia.arbeidsgiver.helper.stengTema
import no.nav.fia.arbeidsgiver.helper.svarPåSpørsmål
import no.nav.fia.arbeidsgiver.helper.åpneTema
import no.nav.fia.arbeidsgiver.konfigurasjon.Topic
import no.nav.fia.arbeidsgiver.konfigurasjon.plugins.HEADER_SESJON_ID
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.DELTAKER_BASEPATH
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.BliMedDto
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.IdentifiserbartSpørsmålDto
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.Spørreundersøkelse
import no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.SpørreundersøkelseSvarProdusent.SpørreundersøkelseSvarDTO
import org.junit.After
import org.junit.Before
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertNotNull

class SpørreundersøkelseDeltakerTest {
    private val topic = Topic.SPØRREUNDERSØKELSE_SVAR
    private val konsument = kafka.nyKonsument(consumerGroupId = topic.konsumentGruppe)

    @Before
    fun setUp() {
        konsument.subscribe(mutableListOf(topic.navn))
    }

    @After
    fun tearDown() {
        konsument.unsubscribe()
        konsument.close()
    }

    @Test
    fun `skal få type tilbake`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val spørreundersøkelse = kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId).tilDomene()

        runBlocking {
            val bliMedDTO = applikasjon.bliMed(spørreundersøkelseId = spørreundersøkelseId)
            val startDto = applikasjon.hentFørsteSpørsmål(bliMedDTO)

            spørreundersøkelse.åpneSpørsmålOgHentSomDeltaker(
                spørsmål = IdentifiserbartSpørsmålDto(temaId = startDto.temaId, spørsmålId = startDto.spørsmålId),
                bliMedDTO = bliMedDTO,
            ).type shouldBe spørreundersøkelse.type
        }
    }

    @Test
    fun `skal verifisere sesjonsid for deltakere`() {
        val spørreundersøkelseId = UUID.randomUUID()
        kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId).tilDomene()

        runBlocking {
            shouldFail {
                applikasjon.hentFørsteSpørsmål(
                    bliMedDTO = BliMedDto(
                        spørreundersøkelseId = spørreundersøkelseId.toString(),
                        sesjonsId = UUID.randomUUID().toString(),
                    ),
                )
            }
        }
    }

    @Test
    fun `skal ikke kunne bli med i en spørreundersøkelse som ikke er påbegynt`() {
        val spørreundersøkelseId = UUID.randomUUID()
        kafka.sendSpørreundersøkelse(
            spørreundersøkelseId = spørreundersøkelseId,
            spørreundersøkelse = kafka.enStandardSpørreundersøkelse(
                id = spørreundersøkelseId,
                spørreundersøkelseStatus = OPPRETTET,
            ),
        )

        runBlocking { shouldFail { applikasjon.bliMed(spørreundersøkelseId) } }
    }

    @Test
    fun `skal ikke kunne svare på spørsmål etter at en undersøkelse er avsluttet`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val spørreundersøkelseFraKafka = kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId)

        runBlocking {
            val bliMedDTO = applikasjon.bliMed(spørreundersøkelseId = spørreundersøkelseId)
            val startDto = applikasjon.hentFørsteSpørsmål(bliMedDTO = bliMedDTO)

            kafka.sendSpørreundersøkelse(
                spørreundersøkelseId = spørreundersøkelseId,
                spørreundersøkelse = spørreundersøkelseFraKafka.copy(status = AVSLUTTET),
            )

            shouldFail {
                val spørsmål = spørreundersøkelseFraKafka.tilDomene().hentSpørsmålITema(startDto)
                applikasjon.svarPåSpørsmål(
                    spørsmål = startDto,
                    svarIder = listOf(spørsmål?.svaralternativer?.first()?.id.toString()),
                    bliMedDTO = bliMedDTO,
                )
            }
            applikasjon shouldContainLog "Spørreundersøkelse med id '$spørreundersøkelseId' er avsluttet".toRegex()
        }
    }

    @Test
    fun `skal kunne finne ut hvilket tema og spørsmål som er det første`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val spørreundersøkelse = kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId).tilDomene()

        runBlocking {
            val bliMedDTO = applikasjon.bliMed(spørreundersøkelseId = spørreundersøkelseId)
            val startDto = applikasjon.hentFørsteSpørsmål(bliMedDTO = bliMedDTO)
            startDto.temaId shouldBe spørreundersøkelse.temaer.first().id
            startDto.spørsmålId shouldBe spørreundersøkelse.temaer.first().spørsmål.first().id.toString()
        }
    }

    @Test
    fun `deltaker får NOT_FOUND dersom spørsmål ikke er funnet`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val spørreundersøkelse = kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId)
        val temaId = spørreundersøkelse.temaer.first().id

        runBlocking {
            val bliMedDTO = applikasjon.bliMed(spørreundersøkelseId = spørreundersøkelseId)

            val randomSpørsmålId = UUID.randomUUID()
            applikasjon.performGet(
                url = "$DELTAKER_BASEPATH/$spørreundersøkelseId/tema/$temaId/sporsmal/$randomSpørsmålId",
            ) {
                header(HEADER_SESJON_ID, bliMedDTO.sesjonsId)
            }.status shouldBe HttpStatusCode.NotFound

            applikasjon shouldContainLog "Fant ikke tema til spørsmålId $randomSpørsmålId".toRegex()
        }
    }

    @Test
    fun `deltaker får NOT_FOUND dersom tema ikke er funnet`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val spørreundersøkelse = kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId).tilDomene()

        runBlocking {
            val bliMedDTO = applikasjon.bliMed(spørreundersøkelseId = spørreundersøkelseId)
            val spørsmålId = spørreundersøkelse.temaer.first().spørsmål.first().id

            val temaId = -1

            applikasjon.performGet(
                url = "$DELTAKER_BASEPATH/$spørreundersøkelseId/tema/$temaId/sporsmal/$spørsmålId",
            ) {
                header(HEADER_SESJON_ID, bliMedDTO.sesjonsId)
            }.status shouldBe HttpStatusCode.NotFound

            applikasjon shouldContainLog "TemaId ikke funnet i spørreundersøkelse".toRegex()
        }
    }

    @Test
    fun `deltaker skal kunne hente spørsmål i en spørreundersøkelse med flere temaer`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val spørreundersøkelse = kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId).tilDomene()

        runBlocking {
            val bliMedDTO = applikasjon.bliMed(spørreundersøkelseId = spørreundersøkelseId)
            val førsteTema = spørreundersøkelse.temaer.first()
            val sisteTema = spørreundersøkelse.temaer.last()
            val spørsmålIdFørsteTema = førsteTema.spørsmål.first().id.toString()
            val spørsmålIdSisteTema = sisteTema.spørsmål.first().id.toString()

            spørreundersøkelse.åpneSpørsmålOgHentSomDeltaker(
                spørsmål = IdentifiserbartSpørsmålDto(temaId = førsteTema.id, spørsmålId = spørsmålIdFørsteTema),
                bliMedDTO = bliMedDTO,
            ) shouldNotBe null

            spørreundersøkelse.åpneSpørsmålOgHentSomDeltaker(
                spørsmål = IdentifiserbartSpørsmålDto(temaId = sisteTema.id, spørsmålId = spørsmålIdSisteTema),
                bliMedDTO = bliMedDTO,
            ) shouldNotBe null
        }
    }

    @Test
    fun `deltaker skal kunne få kategori på spørsmål i en evaluering`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val spørreundersøkelse = kafka.sendSpørreundersøkelse(
            spørreundersøkelseId = spørreundersøkelseId,
            spørreundersøkelse = kafka.enStandardEvaluering(
                id = spørreundersøkelseId,
            ),
        ).tilDomene()

        runBlocking {
            val bliMedDTO = applikasjon.bliMed(spørreundersøkelseId = spørreundersøkelseId)
            val førsteTema = spørreundersøkelse.temaer.first()
            val spørsmålIdFørsteTema = førsteTema.spørsmål.first().id.toString()

            val deltakerSpmDto = spørreundersøkelse.åpneSpørsmålOgHentSomDeltaker(
                spørsmål = IdentifiserbartSpørsmålDto(temaId = førsteTema.id, spørsmålId = spørsmålIdFørsteTema),
                bliMedDTO = bliMedDTO,
            )

            listOf("Utvikle IA-arbeidet", "Veien videre") shouldContain deltakerSpmDto.spørsmål.kategori
        }
    }

    @Test
    fun `deltaker skal få neste spørsmålId når hen henter et spørsmål`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val spørreundersøkelse = kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId).tilDomene()

        runBlocking {
            val bliMedDTO = applikasjon.bliMed(spørreundersøkelseId = spørreundersøkelseId)
            val startDto = applikasjon.hentFørsteSpørsmål(bliMedDTO = bliMedDTO)

            val spørsmål = spørreundersøkelse.åpneSpørsmålOgHentSomDeltaker(
                spørsmål = startDto,
                bliMedDTO = bliMedDTO,
            )

            spørsmål.nesteSpørsmål?.spørsmålId shouldBe spørreundersøkelse.nesteSpørsmålITema(
                temaId = startDto.temaId,
                spørsmålId = startDto.spørsmålId,
            )?.id.toString()

            // -- Test at "nesteSpørsmål" for siste spørsmål i ett tema peker på første spørsmål i neste tema
            val sisteSpørsmålITema = spørreundersøkelse.åpneSpørsmålOgHentSomDeltaker(
                spørsmål = IdentifiserbartSpørsmålDto(
                    temaId = startDto.temaId,
                    spørsmålId = spørreundersøkelse.temaer.first().spørsmål.last().id.toString(),
                ),
                bliMedDTO = bliMedDTO,
            )
            sisteSpørsmålITema.nesteSpørsmål?.temaId shouldBe spørreundersøkelse.temaer[1].id
            sisteSpørsmålITema.nesteSpørsmål?.spørsmålId shouldBe spørreundersøkelse.temaer[1].spørsmål.first().id.toString()
        }
    }

    @Test
    fun `skal kunne få spørsmål først når verten har åpnet spørsmålet`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val spørreundersøkelse = kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId).tilDomene()

        runBlocking {
            val bliMedDto = applikasjon.bliMed(spørreundersøkelseId = spørreundersøkelseId)
            val startDto = applikasjon.hentFørsteSpørsmål(bliMedDTO = bliMedDto)
            val førsteTema = spørreundersøkelse.temaer.first()
            val førsteSpørsmål = førsteTema.spørsmål.first()

            // -- Vert har ikke åpnet spørsmål ennå
            applikasjon.hentSpørsmålSomDeltaker(
                spørsmål = startDto,
                bliMedDTO = bliMedDto,
            ) shouldBe null

            val deltakerSpørsmålDto = spørreundersøkelse.åpneSpørsmålOgHentSomDeltaker(
                spørsmål = startDto,
                bliMedDTO = bliMedDto,
            )
            deltakerSpørsmålDto.spørsmål.tekst shouldBe førsteSpørsmål.tekst
            deltakerSpørsmålDto.spørsmål.svaralternativer.first().id shouldBe førsteSpørsmål.svaralternativer.first().id.toString()
            deltakerSpørsmålDto.spørsmål.svaralternativer.last().id shouldBe førsteSpørsmål.svaralternativer.last().id.toString()
            deltakerSpørsmålDto.spørsmål.svaralternativer.first().tekst shouldBe førsteSpørsmål.svaralternativer.first().svartekst
            deltakerSpørsmålDto.spørsmål.svaralternativer.last().tekst shouldBe førsteSpørsmål.svaralternativer.last().svartekst
            deltakerSpørsmålDto.nesteSpørsmål?.spørsmålId shouldBe førsteTema.spørsmål[1].id.toString()
            deltakerSpørsmålDto.nesteSpørsmål?.temaId shouldBe førsteTema.id
            deltakerSpørsmålDto.temanummer shouldBe 1
            deltakerSpørsmålDto.antallTema shouldBe spørreundersøkelse.temaer.size
            deltakerSpørsmålDto.spørsmålnummer shouldBe 1
            deltakerSpørsmålDto.antallSpørsmål shouldBe førsteTema.spørsmål.size
        }
    }

    @Test
    fun `deltaker skal kunne svare på ett spørsmål`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val spørreundersøkelse = kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId).tilDomene()

        runBlocking {
            val bliMedDTO = applikasjon.bliMed(spørreundersøkelseId = spørreundersøkelseId)
            val startDto = applikasjon.hentFørsteSpørsmål(bliMedDTO = bliMedDTO)
            val spørsmål = spørreundersøkelse.hentSpørsmålITema(startDto)
            applikasjon.svarPåSpørsmål(
                spørsmål = startDto,
                svarIder = listOf(spørsmål?.svaralternativer?.first()?.id.toString()),
                bliMedDTO = bliMedDTO,
            )

            kafka.ventOgKonsumerKafkaMeldinger(
                key = "${bliMedDTO.sesjonsId}_${spørsmål?.id}",
                konsument = konsument,
            ) { meldinger ->
                val deserialiserteSvar = meldinger.map {
                    Json.decodeFromString<SpørreundersøkelseSvarDTO>(it)
                }
                deserialiserteSvar shouldHaveAtLeastSize 1
                deserialiserteSvar.forAtLeastOne { svar ->
                    svar.spørreundersøkelseId shouldBe spørreundersøkelseId.toString()
                    svar.sesjonId shouldBe bliMedDTO.sesjonsId
                    svar.spørsmålId shouldBe spørsmål?.id.toString()
                }
            }
        }
    }

    @Test
    fun `skal ikke kunne svare på en ukjent spørreundersøkelse`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val spørreundersøkelse = kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId).tilDomene()

        runBlocking {
            val bliMedDTO = applikasjon.bliMed(spørreundersøkelseId = spørreundersøkelseId)
            val startDto = applikasjon.hentFørsteSpørsmål(bliMedDTO = bliMedDTO)
            shouldFail {
                applikasjon.svarPåSpørsmål(
                    spørsmål = startDto,
                    svarIder = listOf(
                        spørreundersøkelse.hentSpørsmålITema(startDto)?.svaralternativer?.first()?.id.toString(),
                    ),
                    bliMedDTO = bliMedDTO.copy(spørreundersøkelseId = UUID.randomUUID().toString()),
                )
            }
            // -- tester på "Ugyldig sesjonsId", da vi verifiserer sesjonsId mot spørreundersøkelsesId
            applikasjon shouldContainLog "Ugyldig sesjonId".toRegex()
        }
    }

    @Test
    fun `skal ikke kunne svare med ukjent svarId`() {
        val spørreundersøkelseId = UUID.randomUUID()
        kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId)

        runBlocking {
            val bliMedDTO = applikasjon.bliMed(spørreundersøkelseId = spørreundersøkelseId)
            val startDto = applikasjon.hentFørsteSpørsmål(bliMedDTO = bliMedDTO)
            val svarId = UUID.randomUUID().toString()
            shouldFail {
                applikasjon.svarPåSpørsmål(
                    spørsmål = startDto,
                    svarIder = listOf(svarId),
                    bliMedDTO = bliMedDTO,
                )
            }
            applikasjon shouldContainLog "Ukjent svar for spørsmålId: \\(${startDto.spørsmålId}\\)".toRegex()
        }
    }

    @Test
    fun `Skal ikke kunne sende flere svar på enkeltvalg spørsmål`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val spørreundersøkelse = kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId).tilDomene()

        runBlocking {
            val bliMedDTO = applikasjon.bliMed(spørreundersøkelseId = spørreundersøkelseId)
            val identifiserbartSpørsmålDto = applikasjon.hentFørsteSpørsmål(bliMedDTO = bliMedDTO)
            val spørsmål = spørreundersøkelse.hentSpørsmålITema(identifiserbartSpørsmålDto)

            applikasjon.åpneTema(
                temaId = identifiserbartSpørsmålDto.temaId,
                spørreundersøkelseId = spørreundersøkelseId,
            )
            val deltakerSpørsmålDto =
                applikasjon.hentSpørsmålSomDeltaker(spørsmål = identifiserbartSpørsmålDto, bliMedDTO = bliMedDTO)

            spørsmål?.flervalg shouldBe false
            deltakerSpørsmålDto?.spørsmål?.flervalg shouldBe false
            shouldFail {
                applikasjon.svarPåSpørsmål(
                    spørsmål = identifiserbartSpørsmålDto,
                    svarIder = listOf(
                        spørsmål?.svaralternativer?.first()?.id.toString(),
                        spørsmål?.svaralternativer?.last()?.id.toString(),
                    ),
                    bliMedDTO = bliMedDTO,
                )
            }
            applikasjon shouldContainLog "Spørsmål er ikke flervalg, id: ${spørsmål?.id}".toRegex()
        }
    }

    @Test
    fun `Skal kunne sende flere svar til kafka for på flervalg spørsmål`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val spørreundersøkelse = kafka.sendSpørreundersøkelse(
            spørreundersøkelseId = spørreundersøkelseId,
            spørreundersøkelse = kafka.enStandardSpørreundersøkelse(spørreundersøkelseId, flervalg = true),
        ).tilDomene()

        runBlocking {
            val bliMedDTO = applikasjon.bliMed(spørreundersøkelseId = spørreundersøkelseId)
            val identifiserbartSpørsmålDto = applikasjon.hentFørsteSpørsmål(bliMedDTO = bliMedDTO)
            val spørsmål = spørreundersøkelse.hentSpørsmålITema(identifiserbartSpørsmålDto)

            applikasjon.åpneTema(
                temaId = identifiserbartSpørsmålDto.temaId,
                spørreundersøkelseId = spørreundersøkelseId,
            )
            val deltakerSpørsmålDto =
                applikasjon.hentSpørsmålSomDeltaker(spørsmål = identifiserbartSpørsmålDto, bliMedDTO = bliMedDTO)

            spørsmål?.flervalg shouldBe true
            deltakerSpørsmålDto?.spørsmål?.flervalg shouldBe true

            applikasjon.svarPåSpørsmål(
                spørsmål = identifiserbartSpørsmålDto,
                svarIder = listOf(
                    spørsmål?.svaralternativer?.first()?.id.toString(),
                    spørsmål?.svaralternativer?.last()?.id.toString(),
                ),
                bliMedDTO = bliMedDTO,
            )

            kafka.ventOgKonsumerKafkaMeldinger(
                key = "${bliMedDTO.sesjonsId}_${spørsmål?.id}",
                konsument = konsument,
            ) { meldinger ->
                val deserialiserteSvar = meldinger.map {
                    Json.decodeFromString<SpørreundersøkelseSvarDTO>(it)
                }
                deserialiserteSvar shouldHaveAtLeastSize 1
                deserialiserteSvar.forAtLeastOne { svar ->
                    svar.spørreundersøkelseId shouldBe spørreundersøkelseId.toString()
                    svar.sesjonId shouldBe bliMedDTO.sesjonsId
                    svar.spørsmålId shouldBe spørsmål?.id.toString()
                    svar.svarIder shouldBe listOf(
                        spørsmål?.svaralternativer?.first()?.id.toString(),
                        spørsmål?.svaralternativer?.last()?.id.toString(),
                    )
                }
            }
        }
    }

    @Test
    fun `deltaker skal ikke kunne svare på spørsmål i stengte temaer`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val spørreundersøkelse = kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId).tilDomene()

        runBlocking {
            val bliMedDTO = applikasjon.bliMed(spørreundersøkelseId = spørreundersøkelseId)
            val startDto = applikasjon.hentFørsteSpørsmål(bliMedDTO = bliMedDTO)
            val spørsmål =
                spørreundersøkelse.åpneSpørsmålOgHentSomDeltaker(spørsmål = startDto, bliMedDTO = bliMedDTO)

            applikasjon.stengTema(
                temaId = startDto.temaId,
                spørreundersøkelseId = spørreundersøkelse.id,
            )
            applikasjon.svarPåSpørsmål(
                startDto,
                listOf(spørsmål.spørsmål.svaralternativer.first().id),
                bliMedDTO,
            )
            applikasjon shouldContainLog "Tema '${startDto.temaId}' er stengt, hent nytt spørsmål".toRegex()
        }
    }

    @Test
    fun `deltaker skal ikke kunne svare på spørsmål om alle temaer er stengt`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val spørreundersøkelse = kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId).tilDomene()

        runBlocking {
            val bliMedDTO = applikasjon.bliMed(spørreundersøkelseId = spørreundersøkelseId)
            val startDtoFørsteSpørsmål = applikasjon.hentFørsteSpørsmål(bliMedDTO = bliMedDTO)

            val deltakerSpørsmålDto =
                spørreundersøkelse.åpneSpørsmålOgHentSomDeltaker(
                    spørsmål = startDtoFørsteSpørsmål,
                    bliMedDTO = bliMedDTO,
                )

            spørreundersøkelse.temaer.forEach {
                applikasjon.stengTema(
                    temaId = it.id,
                    spørreundersøkelseId = spørreundersøkelse.id,
                )
            }

            shouldFail {
                applikasjon.svarPåSpørsmål(
                    startDtoFørsteSpørsmål,
                    listOf(deltakerSpørsmålDto.spørsmål.svaralternativer.first().id),
                    bliMedDTO,
                )
            }

            applikasjon shouldContainLog "Alle temaer er stengt for spørreundersøkelse '$spørreundersøkelseId'".toRegex()
        }
    }

    @Test
    fun `deltaker skal kunne hente første spørsmål på andre tema om første er stengt`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val spørreundersøkelse = kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId).tilDomene()

        val førsteTema = spørreundersøkelse.temaer[0]
        val andreTema = spørreundersøkelse.temaer[1]
        val førsteSpørsmålFørsteTema = førsteTema.spørsmål.first()
        val førsteSpørsmålAndreTema = andreTema.spørsmål.first()

        runBlocking {
            val bliMedDTO = applikasjon.bliMed(spørreundersøkelseId = spørreundersøkelseId)
            val startDto = applikasjon.hentFørsteSpørsmål(bliMedDTO = bliMedDTO)

            startDto.spørsmålId shouldBe førsteSpørsmålFørsteTema.id.toString()
            startDto.temaId shouldBe førsteTema.id

            applikasjon.stengTema(
                temaId = startDto.temaId,
                spørreundersøkelseId = spørreundersøkelse.id,
            )
            val nyStartDto = applikasjon.hentFørsteSpørsmål(bliMedDTO = bliMedDTO)

            nyStartDto.spørsmålId shouldBe førsteSpørsmålAndreTema.id.toString()
            nyStartDto.temaId shouldBe andreTema.id
        }
    }
}

private suspend fun Spørreundersøkelse.åpneSpørsmålOgHentSomDeltaker(
    spørsmål: IdentifiserbartSpørsmålDto,
    bliMedDTO: BliMedDto,
) = applikasjon.åpneTema(
    spørsmål.temaId,
    spørreundersøkelseId = id,
).let {
    val deltakerSpørsmålDto = applikasjon.hentSpørsmålSomDeltaker(spørsmål = spørsmål, bliMedDTO = bliMedDTO)
    assertNotNull(deltakerSpørsmålDto)
    deltakerSpørsmålDto
}
