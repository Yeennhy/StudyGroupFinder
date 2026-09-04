package com.studyfinder.app.model

/**
 * `users/{uid}/inbox/{itemId}` — invites and notifications merged into
 * one screen.
 *
 */
data class InboxItem(
    val id: String = "",
    val type: InboxType = InboxType.SYSTEM,
    /** Null for pure system messages with no session attached. */
    val sessionId: String? = null,
    val fromUid: String? = null,
    val message: String = "",
    val read: Boolean = false,
    val createdAtMillis: Long = 0L,
) {
    /** Invite rows carry two buttons — Accept and Details. */
    val isActionable: Boolean get() = type == InboxType.INVITE && sessionId != null
}
