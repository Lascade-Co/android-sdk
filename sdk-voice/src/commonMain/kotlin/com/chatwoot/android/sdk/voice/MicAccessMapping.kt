package com.chatwoot.android.sdk.voice

/** A denied Android permission can still be requested again while the system shows a rationale. */
internal fun androidMicAccess(granted: Boolean, askedBefore: Boolean, rationale: Boolean): MicAccess = when {
    granted -> MicAccess.Granted
    !askedBefore || rationale -> MicAccess.Askable
    else -> MicAccess.Blocked
}
