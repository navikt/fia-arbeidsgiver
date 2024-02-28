package no.nav.fia.arbeidsgiver.sporreundersokelse.domene

import kotlinx.serialization.Serializable
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.SpørsmålOgSvaralternativerDTO
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.SpørsmålOgSvaralternativerTilFrontendDTO
import no.nav.fia.arbeidsgiver.sporreundersokelse.api.dto.SvaralternativDTO
import no.nav.fia.arbeidsgiver.sporreundersokelse.util.UUIDSerializer
import java.util.*

@Serializable
data class SpørsmålOgSvaralternativer (
	@Serializable(with = UUIDSerializer::class)
    val id: UUID,
	val kategori: Kategori,
	val spørsmål: String,
	val antallSvar: Int = 0,
	val svaralternativer: List<Svaralternativ>
) {
    fun toDto(): SpørsmålOgSvaralternativerDTO =
	    SpørsmålOgSvaralternativerDTO(
		    id = this.id,
		    spørsmål = this.spørsmål,
		    svaralternativer = this.svaralternativer.map { svaralternativ ->
			    SvaralternativDTO(
				    id = svaralternativ.svarId,
				    tekst = svaralternativ.svartekst
			    )
		    }
	    )

    fun toFrontendDto(indeksTilSpørsmål: Int, indeksTilSisteSpørsmål: Int): SpørsmålOgSvaralternativerTilFrontendDTO =
	    SpørsmålOgSvaralternativerTilFrontendDTO(
		    id = this.id,
		    spørsmålIndeks = indeksTilSpørsmål,
		    sisteSpørsmålIndeks = indeksTilSisteSpørsmål,
		    spørsmål = this.spørsmål,
		    svaralternativer = this.svaralternativer.map { svaralternativ ->
			    SvaralternativDTO(
				    id = svaralternativ.svarId,
				    tekst = svaralternativ.svartekst
			    )
		    },
			kategori = kategori
	    )
}