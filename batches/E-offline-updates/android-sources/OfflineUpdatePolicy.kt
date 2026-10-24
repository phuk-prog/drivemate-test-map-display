package com.drivemate.app.data

/** Approval is global to offline areas; an old job cannot regain permission after a new approval. */
internal object OfflineUpdatePolicy {
    data class Approval(val enabled: Boolean, val revision: Long)
    fun permits(current: Approval, scheduledRevision: Long): Boolean =
        current.enabled && current.revision == scheduledRevision
}
