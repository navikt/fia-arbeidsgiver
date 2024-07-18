package no.nav.fia.arbeidsgiver.sporreundersokelse.api.deltaker

import HEADER_SESJON_ID
import ia.felles.integrasjoner.kafkameldinger.SpørreundersøkelseStatus
import io.kotest.assertions.shouldFail
import io.kotest.inspectors.forAtLeastOne
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.request.header
import io.ktor.http.HttpStatusCode
import java.util.*
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import no.nav.fia.arbeidsgiver.helper.TestContainerHelper.Companion.fiaArbeidsgiverApi
import no.nav.fia.arbeidsgiver.helper.TestContainerHelper.Companion.kafka
import no.nav.fia.arbeidsgiver.helper.TestContainerHelper.Companion.shouldContainLog
import no.nav.fia.arbeidsgiver.helper.bliMed
import no.nav.fia.arbeidsgiver.helper.hentFørsteSpørsmål
import no.nav.fia.arbeidsgiver.helper.hentSpørsmålSomDeltaker
import no.nav.fia.arbeidsgiver.helper.performGet
import no.nav.fia.arbeidsgiver.helper.stengTema
import no.nav.fia.arbeidsgiver.helper.svarPåSpørsmål
import no.nav.fia.arbeidsgiver.helper.åpneTema
import no.nav.fia.arbeidsgiver.konfigurasjon.KafkaTopics
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.DELTAKER_BASEPATH
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.BliMedDto
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.IdentifiserbartSpørsmålDto
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.Spørreundersøkelse
import no.nav.fia.arbeidsgiver.sporreundersokelse.kafka.SpørreundersøkelseSvarProdusent.SpørreundersøkelseSvarDTO
import org.junit.After
import org.junit.Before

class SpørreundersøkelseDeltakerTest {
    private val spørreundersøkelseSvarKonsument =
        kafka.nyKonsument(topic = KafkaTopics.SPØRREUNDERSØKELSE_SVAR)

    @Before
    fun setUp() {
        spørreundersøkelseSvarKonsument.subscribe(mutableListOf(KafkaTopics.SPØRREUNDERSØKELSE_SVAR.navnMedNamespace))
    }

    @After
    fun tearDown() {
        spørreundersøkelseSvarKonsument.unsubscribe()
        spørreundersøkelseSvarKonsument.close()
    }

    @Test
    fun `skal verifisere sesjonsid for deltakere`() {
        val spørreundersøkelseId = UUID.randomUUID()

        kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId).tilDomene()

        runBlocking {
            shouldFail {
                fiaArbeidsgiverApi.hentFørsteSpørsmål(
                    bliMedDTO = BliMedDto(
                        spørreundersøkelseId = spørreundersøkelseId.toString(),
                        sesjonsId = UUID.randomUUID().toString(),
                    )
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
                spørreundersøkelseId = spørreundersøkelseId,
                spørreundersøkelseStatus = SpørreundersøkelseStatus.OPPRETTET
            )
        )

        runBlocking {
            shouldFail {
                fiaArbeidsgiverApi.bliMed(spørreundersøkelseId)
            }
        }
    }

    @Test
    fun `skal ikke kunne svare på spørsmål etter at en undersøkelse er avsluttet`() {
        val spørreundersøkelseId = UUID.randomUUID()

        val serializableSpørreundersøkelse = kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId)

        runBlocking {
            val bliMedDTO = fiaArbeidsgiverApi.bliMed(spørreundersøkelseId = spørreundersøkelseId)
            val startDto = fiaArbeidsgiverApi.hentFørsteSpørsmål(bliMedDTO = bliMedDTO)

            kafka.sendSpørreundersøkelse(
                spørreundersøkelseId = spørreundersøkelseId,
                spørreundersøkelse = serializableSpørreundersøkelse.copy(status = SpørreundersøkelseStatus.AVSLUTTET)
            )


            shouldFail {
                val spørsmål = serializableSpørreundersøkelse.tilDomene().hentSpørsmålITema(startDto)
                fiaArbeidsgiverApi.svarPåSpørsmål(
                    spørsmål = startDto,
                    svarIder = listOf(spørsmål?.svaralternativer?.first()?.id.toString()),
                    bliMedDTO = bliMedDTO
                )
            }
            fiaArbeidsgiverApi shouldContainLog "Spørreundersøkelse med id '$spørreundersøkelseId' har feil status '${SpørreundersøkelseStatus.AVSLUTTET}'".toRegex()
        }
    }

    @Test
    fun `skal kunne finne ut hvilket tema og spørsmål som er det første`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val spørreundersøkelse = kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId).tilDomene()

        runBlocking {
            val bliMedDTO = fiaArbeidsgiverApi.bliMed(spørreundersøkelseId = spørreundersøkelseId)
            val startDto = fiaArbeidsgiverApi.hentFørsteSpørsmål(bliMedDTO = bliMedDTO)
            startDto.temaId shouldBe spørreundersøkelse.temaer.first().id
            startDto.spørsmålId shouldBe spørreundersøkelse.temaer.first().spørsmål.first().id.toString()
        }
    }

    @Test
    fun `deltaker får NOT_FOUND dersom spørsmål ikke er funnet`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val spørsmålsoversiktDto = kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId)
        val temaId = spørsmålsoversiktDto.temaMedSpørsmålOgSvaralternativer.first().temaId

        runBlocking {
            val bliMedDTO = fiaArbeidsgiverApi.bliMed(spørreundersøkelseId = spørreundersøkelseId)

            val randomSpørsmålId = UUID.randomUUID()
            fiaArbeidsgiverApi.performGet(
                url = "$DELTAKER_BASEPATH/$spørreundersøkelseId/tema/$temaId/sporsmal/$randomSpørsmålId"
            ) {
                header(HEADER_SESJON_ID, bliMedDTO.sesjonsId)
            }.status shouldBe HttpStatusCode.NotFound

            fiaArbeidsgiverApi shouldContainLog "Fant ikke tema til spørsmålId $randomSpørsmålId".toRegex()
        }
    }

    @Test
    fun `deltaker får NOT_FOUND dersom tema ikke er funnet`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val spørreundersøkelse = kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId).tilDomene()

        runBlocking {
            val bliMedDTO = fiaArbeidsgiverApi.bliMed(spørreundersøkelseId = spørreundersøkelseId)
            val spørsmålId = spørreundersøkelse.temaer.first().spørsmål.first().id

            val temaId = -1

            fiaArbeidsgiverApi.performGet(
                url = "$DELTAKER_BASEPATH/$spørreundersøkelseId/tema/$temaId/sporsmal/$spørsmålId"
            ) {
                header(HEADER_SESJON_ID, bliMedDTO.sesjonsId)
            }.status shouldBe HttpStatusCode.NotFound

            fiaArbeidsgiverApi shouldContainLog "TemaId ikke funnet i spørreundersøkelse".toRegex()
        }
    }

    @Test
    fun `deltaker skal kunne hente spørsmål i en spørreundersøkelse med flere temaer`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val spørreundersøkelse = kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId).tilDomene()

        runBlocking {
            val bliMedDTO = fiaArbeidsgiverApi.bliMed(spørreundersøkelseId = spørreundersøkelseId)

            val førsteTema = spørreundersøkelse.temaer.first()
            val sisteTema = spørreundersøkelse.temaer.last()
            val spørsmålIdFørsteTema = førsteTema.spørsmål.first().id.toString()
            val spørsmålIdSisteTema = sisteTema.spørsmål.first().id.toString()

            spørreundersøkelse.åpneSpørsmålOgHentSomDeltaker(
                spørsmål = IdentifiserbartSpørsmålDto(temaId = førsteTema.id, spørsmålId = spørsmålIdFørsteTema),
                bliMedDTO = bliMedDTO
            ) shouldNotBe null


            spørreundersøkelse.åpneSpørsmålOgHentSomDeltaker(
                spørsmål = IdentifiserbartSpørsmålDto(temaId = sisteTema.id, spørsmålId = spørsmålIdSisteTema),
                bliMedDTO = bliMedDTO
            ) shouldNotBe null
        }
    }

    @Test
    fun `deltaker skal få neste spørsmålId når hen henter et spørsmål`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val spørreundersøkelse = kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId).tilDomene()

        runBlocking {
            val bliMedDTO = fiaArbeidsgiverApi.bliMed(spørreundersøkelseId = spørreundersøkelseId)
            val startDto = fiaArbeidsgiverApi.hentFørsteSpørsmål(bliMedDTO = bliMedDTO)

            val spørsmål = spørreundersøkelse.åpneSpørsmålOgHentSomDeltaker(
                spørsmål = startDto,
                bliMedDTO = bliMedDTO
            )

            spørsmål.nesteSpørsmål?.spørsmålId shouldBe spørreundersøkelse.nesteSpørsmålITema(
                temaId = startDto.temaId,
                spørsmålId = startDto.spørsmålId
            )?.id.toString()

            // -- Test at "nesteSpørsmål" for siste spørsmål i ett tema peker på første spørsmål i neste tema
            val sisteSpørsmålITema = spørreundersøkelse.åpneSpørsmålOgHentSomDeltaker(
                spørsmål = IdentifiserbartSpørsmålDto(
                    temaId = startDto.temaId,
                    spørsmålId = spørreundersøkelse.temaer.first().spørsmål.last().id.toString()
                ),
                bliMedDTO = bliMedDTO
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
            val bliMedDto = fiaArbeidsgiverApi.bliMed(spørreundersøkelseId = spørreundersøkelseId)
            val startDto = fiaArbeidsgiverApi.hentFørsteSpørsmål(bliMedDTO = bliMedDto)
            val førsteTema = spørreundersøkelse.temaer.first()
            val førsteSpørsmål = førsteTema.spørsmål.first()

            // -- Vert har ikke åpnet spørsmål ennå
            fiaArbeidsgiverApi.hentSpørsmålSomDeltaker(
                spørsmål = startDto,
                bliMedDTO = bliMedDto
            ) shouldBe null

            val spørsmålsoversiktDto = spørreundersøkelse.åpneSpørsmålOgHentSomDeltaker(
                spørsmål = startDto,
                bliMedDTO = bliMedDto
            )
            spørsmålsoversiktDto.spørsmål.tekst shouldBe førsteSpørsmål.tekst
            spørsmålsoversiktDto.spørsmål.svaralternativer.first().id shouldBe førsteSpørsmål.svaralternativer.first().id.toString()
            spørsmålsoversiktDto.spørsmål.svaralternativer.last().id shouldBe førsteSpørsmål.svaralternativer.last().id.toString()
            spørsmålsoversiktDto.spørsmål.svaralternativer.first().tekst shouldBe førsteSpørsmål.svaralternativer.first().svartekst
            spørsmålsoversiktDto.spørsmål.svaralternativer.last().tekst shouldBe førsteSpørsmål.svaralternativer.last().svartekst
            spørsmålsoversiktDto.nesteSpørsmål?.spørsmålId shouldBe førsteTema.spørsmål[1].id.toString()
            spørsmålsoversiktDto.nesteSpørsmål?.temaId shouldBe førsteTema.id
            spørsmålsoversiktDto.temanummer shouldBe 1
            spørsmålsoversiktDto.antallTema shouldBe spørreundersøkelse.temaer.size
            spørsmålsoversiktDto.spørsmålnummer shouldBe 1
            spørsmålsoversiktDto.antallSpørsmål shouldBe førsteTema.spørsmål.size
        }
    }

    @Test
    fun `deltaker skal kunne svare på ett spørsmål`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val spørreundersøkelse = kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId).tilDomene()

        runBlocking {
            val bliMedDTO = fiaArbeidsgiverApi.bliMed(spørreundersøkelseId = spørreundersøkelseId)
            val startDto = fiaArbeidsgiverApi.hentFørsteSpørsmål(bliMedDTO = bliMedDTO)
            val spørsmål = spørreundersøkelse.hentSpørsmålITema(startDto)
            fiaArbeidsgiverApi.svarPåSpørsmål(
                spørsmål = startDto,
                svarIder = listOf(spørsmål?.svaralternativer?.first()?.id.toString()),
                bliMedDTO = bliMedDTO
            )

            kafka.ventOgKonsumerKafkaMeldinger(
                key = "${bliMedDTO.sesjonsId}_${spørsmål?.id}",
                konsument = spørreundersøkelseSvarKonsument
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
            val bliMedDTO = fiaArbeidsgiverApi.bliMed(spørreundersøkelseId = spørreundersøkelseId)
            val startDto = fiaArbeidsgiverApi.hentFørsteSpørsmål(bliMedDTO = bliMedDTO)
            shouldFail {
                fiaArbeidsgiverApi.svarPåSpørsmål(
                    spørsmål = startDto,
                    svarIder = listOf(
                        spørreundersøkelse.hentSpørsmålITema(startDto)?.svaralternativer?.first()?.id.toString()
                    ),
                    bliMedDTO = bliMedDTO.copy(spørreundersøkelseId = UUID.randomUUID().toString())
                )
            }
            // -- tester på "Ugyldig sesjonsId", da vi verifiserer sesjonsId mot spørreundersøkelsesId
            fiaArbeidsgiverApi shouldContainLog "Ugyldig sesjonId".toRegex()
        }
    }

    @Test
    fun `skal ikke kunne svare med ukjent svarId`() {
        val spørreundersøkelseId = UUID.randomUUID()
        kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId)

        runBlocking {
            val bliMedDTO = fiaArbeidsgiverApi.bliMed(spørreundersøkelseId = spørreundersøkelseId)
            val startDto = fiaArbeidsgiverApi.hentFørsteSpørsmål(bliMedDTO = bliMedDTO)
            val svarId = UUID.randomUUID().toString()
            shouldFail {
                fiaArbeidsgiverApi.svarPåSpørsmål(
                    spørsmål = startDto,
                    svarIder = listOf(svarId),
                    bliMedDTO = bliMedDTO
                )
            }
            fiaArbeidsgiverApi shouldContainLog "Ukjent svar for spørsmålId: \\(${startDto.spørsmålId}\\)".toRegex()
        }
    }

    @Test
    fun `Skal ikke kunne sende flere svar på enkeltvalg spørsmål`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val spørreundersøkelse = kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId).tilDomene()

        runBlocking {
            val bliMedDTO = fiaArbeidsgiverApi.bliMed(spørreundersøkelseId = spørreundersøkelseId)
            val startDto = fiaArbeidsgiverApi.hentFørsteSpørsmål(bliMedDTO = bliMedDTO)
            val spørsmål = spørreundersøkelse.hentSpørsmålITema(startDto)

            fiaArbeidsgiverApi.åpneTema(
                temaId = startDto.temaId,
                spørreundersøkelseId = spørreundersøkelseId,
            )
            val spørsmålsoversiktDeltaker =
                fiaArbeidsgiverApi.hentSpørsmålSomDeltaker(spørsmål = startDto, bliMedDTO = bliMedDTO)

            spørsmål?.flervalg shouldBe false
            spørsmålsoversiktDeltaker?.spørsmål?.flervalg shouldBe false
            shouldFail {
                fiaArbeidsgiverApi.svarPåSpørsmål(
                    spørsmål = startDto,
                    svarIder = listOf(
                        spørsmål?.svaralternativer?.first()?.id.toString(),
                        spørsmål?.svaralternativer?.last()?.id.toString()
                    ),
                    bliMedDTO = bliMedDTO
                )
            }
            fiaArbeidsgiverApi shouldContainLog "Spørsmål er ikke flervalg, id: ${spørsmål?.id}".toRegex()
        }
    }

    @Test
    fun `Skal kunne sende flere svar til kafka for på flervalg spørsmål`() {
        val spørreundersøkelseId = UUID.randomUUID()

        val spørreundersøkelse = kafka.sendSpørreundersøkelse(
            spørreundersøkelseId = spørreundersøkelseId,
            spørreundersøkelse = kafka.enStandardSpørreundersøkelse(spørreundersøkelseId, flervalg = true)
        ).tilDomene()

        runBlocking {
            val bliMedDTO = fiaArbeidsgiverApi.bliMed(spørreundersøkelseId = spørreundersøkelseId)
            val startDto = fiaArbeidsgiverApi.hentFørsteSpørsmål(bliMedDTO = bliMedDTO)
            val spørsmål = spørreundersøkelse.hentSpørsmålITema(startDto)

            fiaArbeidsgiverApi.åpneTema(
                temaId = startDto.temaId,
                spørreundersøkelseId = spørreundersøkelseId,
            )
            val spørsmålsoversiktDeltaker =
                fiaArbeidsgiverApi.hentSpørsmålSomDeltaker(spørsmål = startDto, bliMedDTO = bliMedDTO)

            spørsmål?.flervalg shouldBe true
            spørsmålsoversiktDeltaker?.spørsmål?.flervalg shouldBe true

            fiaArbeidsgiverApi.svarPåSpørsmål(
                spørsmål = startDto,
                svarIder = listOf(
                    spørsmål?.svaralternativer?.first()?.id.toString(),
                    spørsmål?.svaralternativer?.last()?.id.toString()
                ),
                bliMedDTO = bliMedDTO
            )

            kafka.ventOgKonsumerKafkaMeldinger(
                key = "${bliMedDTO.sesjonsId}_${spørsmål?.id}",
                konsument = spørreundersøkelseSvarKonsument
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
                        spørsmål?.svaralternativer?.last()?.id.toString()
                    )
                }
            }
        }
    }

    @Test
    fun `deltaker skal ikke kunne svare på spørsmål i stengte temaer`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val spørreundersøkelse =
            kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId).tilDomene()

        runBlocking {
            val bliMedDTO = fiaArbeidsgiverApi.bliMed(spørreundersøkelseId = spørreundersøkelseId)
            val startDto = fiaArbeidsgiverApi.hentFørsteSpørsmål(bliMedDTO = bliMedDTO)
            val spørsmål =
                spørreundersøkelse.åpneSpørsmålOgHentSomDeltaker(spørsmål = startDto, bliMedDTO = bliMedDTO)

            fiaArbeidsgiverApi.stengTema(
                temaId = startDto.temaId,
                spørreundersøkelseId = spørreundersøkelse.id,
            )
            fiaArbeidsgiverApi.svarPåSpørsmål(
                startDto,
                listOf(spørsmål.spørsmål.svaralternativer.first().id),
                bliMedDTO
            )
            fiaArbeidsgiverApi shouldContainLog "Tema '${startDto.temaId}' er stengt, hent nytt spørsmål".toRegex()
        }
    }

    @Test
    fun `deltaker skal ikke kunne svare på spørsmål om alle temaer er stengt`() {
        val spørreundersøkelseId = UUID.randomUUID()
        val spørreundersøkelse = kafka.sendSpørreundersøkelse(spørreundersøkelseId = spørreundersøkelseId).tilDomene()

        runBlocking {
            val bliMedDTO = fiaArbeidsgiverApi.bliMed(spørreundersøkelseId = spørreundersøkelseId)
            val startDtoFørsteSpørsmål = fiaArbeidsgiverApi.hentFørsteSpørsmål(bliMedDTO = bliMedDTO)

            val spørsmål =
                spørreundersøkelse.åpneSpørsmålOgHentSomDeltaker(
                    spørsmål = startDtoFørsteSpørsmål,
                    bliMedDTO = bliMedDTO
                )

            spørreundersøkelse.temaer.forEach {
                fiaArbeidsgiverApi.stengTema(
                    temaId = it.id,
                    spørreundersøkelseId = spørreundersøkelse.id,
                )
            }

            shouldFail {
                fiaArbeidsgiverApi.svarPåSpørsmål(
                    startDtoFørsteSpørsmål,
                    listOf(spørsmål.spørsmål.svaralternativer.first().id),
                    bliMedDTO
                )
            }

            fiaArbeidsgiverApi shouldContainLog "Alle temaer er stengt for spørreundersøkelse '$spørreundersøkelseId'".toRegex()
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
            val bliMedDTO = fiaArbeidsgiverApi.bliMed(spørreundersøkelseId = spørreundersøkelseId)
            val startDto = fiaArbeidsgiverApi.hentFørsteSpørsmål(bliMedDTO = bliMedDTO)

            startDto.spørsmålId shouldBe førsteSpørsmålFørsteTema.id.toString()
            startDto.temaId shouldBe førsteTema.id

            fiaArbeidsgiverApi.stengTema(
                temaId = startDto.temaId,
                spørreundersøkelseId = spørreundersøkelse.id,
            )
            val nyStartDto = fiaArbeidsgiverApi.hentFørsteSpørsmål(bliMedDTO = bliMedDTO)

            nyStartDto.spørsmålId shouldBe førsteSpørsmålAndreTema.id.toString()
            nyStartDto.temaId shouldBe andreTema.id
        }
    }
}

private suspend fun Spørreundersøkelse.åpneSpørsmålOgHentSomDeltaker(
    spørsmål: IdentifiserbartSpørsmålDto,
    bliMedDTO: BliMedDto,
) = fiaArbeidsgiverApi.åpneTema(
    spørsmål.temaId,
    spørreundersøkelseId = id,
).let {
    val spørsmålsoversiktDto = fiaArbeidsgiverApi.hentSpørsmålSomDeltaker(spørsmål = spørsmål, bliMedDTO = bliMedDTO)
    assertNotNull(spørsmålsoversiktDto)
    spørsmålsoversiktDto
}


