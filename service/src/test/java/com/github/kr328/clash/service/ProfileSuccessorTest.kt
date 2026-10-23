package com.github.kr328.clash.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID

class ProfileSuccessorTest {
    private val a = UUID.randomUUID()
    private val b = UUID.randomUUID()
    private val c = UUID.randomUUID()

    @Test
    fun theNextOneTakesItsPlace() {
        assertEquals(b, ProfileProcessor.successor(listOf(a, b, c), a))
        assertEquals(c, ProfileProcessor.successor(listOf(a, b, c), b))
    }

    @Test
    fun theLastOneIsFollowedByTheOneBefore() {
        assertEquals(b, ProfileProcessor.successor(listOf(a, b, c), c))
    }

    @Test
    fun noneLeftIsNone() {
        assertNull(ProfileProcessor.successor(listOf(a), a))
        assertNull(ProfileProcessor.successor(emptyList(), a))
    }

    @Test
    fun anUnlistedOneGivesTheFirst() {
        assertEquals(a, ProfileProcessor.successor(listOf(a, b), c))
    }
}
