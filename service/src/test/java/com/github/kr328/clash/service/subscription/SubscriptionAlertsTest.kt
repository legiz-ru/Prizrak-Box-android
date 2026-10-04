package com.github.kr328.clash.service.subscription

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID
import java.util.concurrent.TimeUnit

class SubscriptionAlertsTest {
    private val minute = TimeUnit.MINUTES.toMillis(1)
    private val hour = TimeUnit.HOURS.toMillis(1)
    private val day = TimeUnit.DAYS.toMillis(1)

    @Test
    fun kindCodeRoundTrips() {
        listOf(
            SubscriptionAlert.Expired,
            SubscriptionAlert.ExpiresIn(3),
            SubscriptionAlert.TrafficUsed(80),
        ).forEach { alert ->
            assertEquals(alert, SubscriptionAlert.fromKindCode(alert.toKindCode()))
        }
    }

    @Test
    fun unknownKindCodeIsRejected() {
        assertNull(SubscriptionAlert.fromKindCode(""))
        assertNull(SubscriptionAlert.fromKindCode("EXPIRES_IN:"))
        assertNull(SubscriptionAlert.fromKindCode("EXPIRES_IN:soon"))
        assertNull(SubscriptionAlert.fromKindCode("TRAFFIC_USED:x"))
        assertNull(SubscriptionAlert.fromKindCode("SOMETHING"))
    }

    @Test
    fun remainingIsWordedByTheCoarsestUsefulUnit() {
        // 4 days minus one minute is still "3 d" — it was "4 days" in the old text.
        assertEquals(
            SubscriptionRemaining.Days(3),
            SubscriptionAlerts.splitRemaining(4 * day - minute),
        )
        assertEquals(SubscriptionRemaining.Days(3), SubscriptionAlerts.splitRemaining(3 * day))
        assertEquals(SubscriptionRemaining.Days(10), SubscriptionAlerts.splitRemaining(10 * day + 5 * hour))

        assertEquals(
            SubscriptionRemaining.DaysHours(2, 23),
            SubscriptionAlerts.splitRemaining(3 * day - minute),
        )
        assertEquals(
            SubscriptionRemaining.DaysHours(1, 0),
            SubscriptionAlerts.splitRemaining(day),
        )
        assertEquals(
            SubscriptionRemaining.DaysHours(1, 5),
            SubscriptionAlerts.splitRemaining(day + 5 * hour + 59 * minute),
        )

        assertEquals(
            SubscriptionRemaining.HoursMinutes(23, 59),
            SubscriptionAlerts.splitRemaining(day - minute),
        )
        assertEquals(
            SubscriptionRemaining.HoursMinutes(1, 0),
            SubscriptionAlerts.splitRemaining(hour),
        )
        assertEquals(
            SubscriptionRemaining.HoursMinutes(5, 20),
            SubscriptionAlerts.splitRemaining(5 * hour + 20 * minute + 30_000),
        )

        assertEquals(
            SubscriptionRemaining.Minutes(59),
            SubscriptionAlerts.splitRemaining(hour - minute),
        )
        assertEquals(SubscriptionRemaining.Minutes(12), SubscriptionAlerts.splitRemaining(12 * minute))
    }

    @Test
    fun lessThanAMinuteStillSaysOneMinute() {
        assertEquals(SubscriptionRemaining.Minutes(1), SubscriptionAlerts.splitRemaining(1))
        assertEquals(SubscriptionRemaining.Minutes(1), SubscriptionAlerts.splitRemaining(59_999))
    }

    @Test
    fun usedPercentIsTheCurrentRoundedDownShare() {
        assertEquals(29, SubscriptionAlerts.usedPercent(29, 100))
        assertEquals(83, SubscriptionAlerts.usedPercent(83_900, 100_000))
        assertEquals(0, SubscriptionAlerts.usedPercent(0, 100))
        assertEquals(100, SubscriptionAlerts.usedPercent(100, 100))
        // Past the quota it says so rather than clamping to 100.
        assertEquals(112, SubscriptionAlerts.usedPercent(112, 100))
    }

    @Test
    fun usedPercentHandlesUnknownQuotaAndNegativeUsage() {
        assertNull(SubscriptionAlerts.usedPercent(10, 0))
        assertNull(SubscriptionAlerts.usedPercent(10, -1))
        assertEquals(0, SubscriptionAlerts.usedPercent(-5, 100))
    }

    @Test
    fun usedPercentDoesNotOverflowOnLargeQuotas() {
        val tb = 1L shl 40
        assertEquals(50, SubscriptionAlerts.usedPercent(5000 * tb, 10_000 * tb))
        assertEquals(99, SubscriptionAlerts.usedPercent(9_999 * tb, 10_000 * tb))
    }

    @Test
    fun notificationIdsAreDistinctPerProfileAndKind() {
        val a = UUID.fromString("00000000-0000-0000-0000-00000000000a")
        val b = UUID.fromString("00000000-0000-0000-0000-00000000000b")

        val ids = listOf(a, b).flatMap { uuid ->
            listOf(
                SubscriptionAlerts.notificationId(uuid, SubscriptionAlert.ExpiresIn(3)),
                SubscriptionAlerts.notificationId(uuid, SubscriptionAlert.Expired),
                SubscriptionAlerts.notificationId(uuid, SubscriptionAlert.TrafficUsed(80)),
            )
        }

        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun notificationIdIgnoresTheThreshold() {
        val uuid = UUID.randomUUID()

        // A fresh "expires in 1 day" must replace the stale "expires in 3 days".
        assertEquals(
            SubscriptionAlerts.notificationId(uuid, SubscriptionAlert.ExpiresIn(3)),
            SubscriptionAlerts.notificationId(uuid, SubscriptionAlert.ExpiresIn(1)),
        )
        assertEquals(
            SubscriptionAlerts.notificationId(uuid, SubscriptionAlert.TrafficUsed(80)),
            SubscriptionAlerts.notificationId(uuid, SubscriptionAlert.TrafficUsed(90)),
        )
        assertNotEquals(
            SubscriptionAlerts.notificationId(uuid, SubscriptionAlert.ExpiresIn(3)),
            SubscriptionAlerts.notificationId(uuid, SubscriptionAlert.TrafficUsed(80)),
        )
    }

    @Test
    fun notificationIdIsStableAcrossProcesses() {
        // Derived from the UUID's bits, not from object identity — the :background
        // process posts the notification and must replace the one an earlier run posted.
        val first = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
        val second = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")

        assertEquals(
            SubscriptionAlerts.notificationId(first, SubscriptionAlert.Expired),
            SubscriptionAlerts.notificationId(second, SubscriptionAlert.Expired),
        )
    }

    @Test
    fun trafficThresholdsStillFollowTheSameArithmetic() {
        val snapshot = SubscriptionAlerts.Snapshot(
            expireAt = 0,
            total = 100,
            used = 83,
            expireDays = emptyList(),
            trafficPercent = listOf(80, 90, 100),
            notified = emptyMap(),
        )

        val outcome = SubscriptionAlerts.evaluate(snapshot, nowMillis = 0)

        assertEquals(listOf<SubscriptionAlert>(SubscriptionAlert.TrafficUsed(80)), outcome.alerts)
    }

    @Test
    fun expiryAlertFiresOncePerThreshold() {
        val now = 1_000_000_000L
        val snapshot = SubscriptionAlerts.Snapshot(
            expireAt = now + 3 * day - hour,
            total = 0,
            used = 0,
            expireDays = listOf(1, 3, 7),
            trafficPercent = emptyList(),
            notified = emptyMap(),
        )

        val first = SubscriptionAlerts.evaluate(snapshot, now)
        assertEquals(listOf<SubscriptionAlert>(SubscriptionAlert.ExpiresIn(3)), first.alerts)

        val second = SubscriptionAlerts.evaluate(snapshot.copy(notified = first.notified), now + minute)
        assertEquals(emptyList<SubscriptionAlert>(), second.alerts)
    }
}
