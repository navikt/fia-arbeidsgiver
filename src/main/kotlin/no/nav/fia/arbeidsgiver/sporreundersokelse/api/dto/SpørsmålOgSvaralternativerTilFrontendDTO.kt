package no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto

import kotlinx.serialization.Serializable
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.Tema

@Serializable
data class SpørsmålOgSvaralternativerTilFrontendDTO (
    val id: String,
    val spørsmålIndeks: Int,
    val sisteSpørsmålIndeks: Int,
    val spørsmål: String,
    val svaralternativer: List<SvaralternativDTO>,
    val tema: Tema,
)
