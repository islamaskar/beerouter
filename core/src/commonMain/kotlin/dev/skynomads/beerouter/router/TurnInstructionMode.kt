package dev.skynomads.beerouter.router

public enum class TurnInstructionMode(public val id: Int) {
    NONE(0),
    AUTO(1),
    LOCUS(2),
    OSMAND(3),
    COMMENT_STYLE(4),
    GPSIES_STYLE(5),
    TRKPT_STYLE(8),
    LOCUS_TRKPT_STYLE(9);

    public companion object {
        private val byId: Map<Int, TurnInstructionMode> = entries.associateBy(TurnInstructionMode::id)

        public fun fromId(id: Int): TurnInstructionMode? =
            byId[id]

        public fun supportsGuideTrackDetours(mode: TurnInstructionMode): Boolean =
            mode != NONE

        public fun supportsGuideTrackDetours(id: Int): Boolean = id > NONE.id

        public fun supportsBeelineHints(mode: TurnInstructionMode): Boolean =
            mode == TRKPT_STYLE ||
                mode == COMMENT_STYLE ||
                mode == LOCUS ||
                mode == LOCUS_TRKPT_STYLE

        public fun supportsBeelineHints(id: Int): Boolean =
            id == TRKPT_STYLE.id ||
                id == COMMENT_STYLE.id ||
                id == LOCUS.id ||
                id == LOCUS_TRKPT_STYLE.id

        public fun usesExplicitExitCommands(mode: TurnInstructionMode): Boolean =
            mode == LOCUS || mode == LOCUS_TRKPT_STYLE

        public fun usesExplicitExitCommands(id: Int): Boolean =
            id == LOCUS.id || id == LOCUS_TRKPT_STYLE.id
    }
}
