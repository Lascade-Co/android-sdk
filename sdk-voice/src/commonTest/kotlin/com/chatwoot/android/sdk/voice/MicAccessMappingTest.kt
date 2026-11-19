package com.chatwoot.android.sdk.voice

import kotlin.test.Test
import kotlin.test.assertEquals

class MicAccessMappingTest {
    @Test
    fun androidPermissionStates() {
        assertEquals(MicAccess.Granted, androidMicAccess(granted = true, askedBefore = false, rationale = false))
        assertEquals(MicAccess.Askable, androidMicAccess(granted = false, askedBefore = false, rationale = false))
        assertEquals(MicAccess.Askable, androidMicAccess(granted = false, askedBefore = true, rationale = true))
        assertEquals(MicAccess.Blocked, androidMicAccess(granted = false, askedBefore = true, rationale = false))
    }
}
