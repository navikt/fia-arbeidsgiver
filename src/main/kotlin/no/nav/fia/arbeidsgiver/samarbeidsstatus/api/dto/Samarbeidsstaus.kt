package no.nav.fia.arbeidsgiver.samarbeidsstatus.api.dto

enum class Samarbeidsstaus {
    IKKE_I_SAMARBEID,
    I_SAMARBEID,
    ;

    companion object {
        fun fraIASakStatus(status: String) =
            when (status) {
                "VI_BISTÅR" -> I_SAMARBEID
                else -> IKKE_I_SAMARBEID
            }
    }
}
