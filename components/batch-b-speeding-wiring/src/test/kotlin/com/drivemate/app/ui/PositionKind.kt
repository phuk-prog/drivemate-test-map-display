package com.drivemate.app.ui
/** Exact PositionKind declaration from private app 9f01c0a1f7154861941eee57fb52a471917b7ce5. */
enum class PositionKind {
    MEASURED, ESTIMATED;
    companion object {
        /** The session's own estimates carry this provider name. */
        const val ESTIMATE_PROVIDER = "estimate"
        fun of(provider: String?): PositionKind = if (provider == ESTIMATE_PROVIDER) ESTIMATED else MEASURED
    }
}
