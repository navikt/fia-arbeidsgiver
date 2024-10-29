package no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto

import kotlinx.serialization.Serializable
import no.nav.fia.arbeidsgiver.sporreundersokelse.domene.Spørreundersøkelse

@Serializable
data class SpørreundersøkelseKontekstDto(
    val type: String,
    val virksomhetsnavn: String,
    val samarbeidsnavn: String? = null
)

fun Spørreundersøkelse.tilSpørreundersøkelseKontekstDto() = SpørreundersøkelseKontekstDto(
    type = this.type,
    virksomhetsnavn = this.virksomhetsNavn,
)
