package com.github.kr328.clash.service

import org.junit.Assert.assertEquals
import org.junit.Test

class SubscriptionExpireTest {
    @Test
    fun aDateIsKeptInMilliseconds() {
        assertEquals(1_800_000_000_000L, ProfileProcessor.expireMillis(1_800_000_000.0))
        assertEquals(4_070_908_799_000L, ProfileProcessor.expireMillis(4_070_908_799.0))
    }

    @Test
    fun noEndIsNoDate() {
        assertEquals(0L, ProfileProcessor.expireMillis(0.0))
        assertEquals(0L, ProfileProcessor.expireMillis(-1.0))
        // Remnawave's "unlimited": 2099 and later.
        assertEquals(0L, ProfileProcessor.expireMillis(4_070_908_800.0))
        assertEquals(0L, ProfileProcessor.expireMillis(4_102_444_800.0))
    }
}
