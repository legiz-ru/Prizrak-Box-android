package com.github.kr328.clash.service.subscription

import java.util.Objects
import java.util.UUID
import java.util.concurrent.TimeUnit

sealed interface SubscriptionAlert {
    data object Expired : SubscriptionAlert

    data class ExpiresIn(val days: Int) : SubscriptionAlert

    data class TrafficUsed(val percent: Int) : SubscriptionAlert

    /**
     * A plain Intent extra can't carry a sealed type, so the notification's tap
     * target (MainActivity) recovers the kind from this coded string — the same
     * convention as the rest of this codebase's cross-boundary reasons
     * (HwidNotSupportedException, FETCH_* — see ProfileProcessor).
     */
    fun toKindCode(): String = when (this) {
        is Expired -> "EXPIRED"
        is ExpiresIn -> "EXPIRES_IN:$days"
        is TrafficUsed -> "TRAFFIC_USED:$percent"
    }

    companion object {
        fun fromKindCode(code: String): SubscriptionAlert? = when {
            code == "EXPIRED" -> Expired
            code.startsWith("EXPIRES_IN:") ->
                code.removePrefix("EXPIRES_IN:").toIntOrNull()?.let { ExpiresIn(it) }
            code.startsWith("TRAFFIC_USED:") ->
                code.removePrefix("TRAFFIC_USED:").toIntOrNull()?.let { TrafficUsed(it) }
            else -> null
        }
    }
}

/** Time left until expiry, split the way it is worded: the coarser the unit, the less is shown. */
sealed interface SubscriptionRemaining {
    data class Days(val days: Int) : SubscriptionRemaining

    data class DaysHours(val days: Int, val hours: Int) : SubscriptionRemaining

    data class HoursMinutes(val hours: Int, val minutes: Int) : SubscriptionRemaining

    data class Minutes(val minutes: Int) : SubscriptionRemaining
}

/**
 * Decides which subscription-state alerts to show, from a plain snapshot of
 * the subscription and of what has already been shown. Knows nothing about
 * notifications, headers, or storage — those are [SubscriptionAlertReporter]'s
 * job, so this stays trivial to reason about and to test.
 */
object SubscriptionAlerts {
    val DEFAULT_EXPIRE_DAYS = listOf(1, 3, 7)

    val DEFAULT_TRAFFIC_PERCENT = listOf(80, 90, 100)

    private val DAY_MILLIS = TimeUnit.DAYS.toMillis(1)

    private val HOUR_MILLIS = TimeUnit.HOURS.toMillis(1)

    private val MINUTE_MILLIS = TimeUnit.MINUTES.toMillis(1)

    private const val EXPIRED_KEY = "expired"

    private const val EXPIRE_BASE_KEY = "expire_base"

    private fun expireKey(days: Int) = "expire_${days}d"

    private fun trafficKey(percent: Int) = "traffic_$percent"

    data class Snapshot(
        val expireAt: Long,
        val total: Long,
        val used: Long,
        val expireDays: List<Int>,
        val trafficPercent: List<Int>,
        val notified: Map<String, Long>,
    )

    data class Outcome(
        val alerts: List<SubscriptionAlert>,
        val notified: Map<String, Long>,
    )

    /**
     * [nowMillis] should already carry the panel clock-skew correction — this
     * function just compares numbers, it doesn't know or care where they came
     * from.
     */
    fun evaluate(snapshot: Snapshot, nowMillis: Long): Outcome {
        val map = snapshot.notified.toMutableMap()

        // Drop bookkeeping for thresholds that no longer apply — the panel
        // changed its mind about which days/percentages matter.
        val valid = { key: String ->
            key == EXPIRE_BASE_KEY ||
                (key == EXPIRED_KEY && snapshot.expireDays.isNotEmpty()) ||
                snapshot.expireDays.any { key == expireKey(it) } ||
                snapshot.trafficPercent.any { key == trafficKey(it) }
        }
        map.keys.retainAll { valid(it) }

        val alerts = mutableListOf<SubscriptionAlert>()

        if (snapshot.expireAt != 0L && snapshot.expireDays.isNotEmpty()) {
            // The expiry moment itself changed (renewal, panel clock fixed) —
            // every prior "shown" mark for this category is about a different
            // deadline and means nothing now.
            if (map[EXPIRE_BASE_KEY] != snapshot.expireAt) {
                map.keys.retainAll { !it.startsWith("expire_") && it != EXPIRED_KEY }
                map[EXPIRE_BASE_KEY] = snapshot.expireAt
            }

            val remaining = snapshot.expireAt - nowMillis

            // Time moved back across a threshold (renewal) — rearm it so the
            // next real crossing notifies again instead of staying "shown".
            if (remaining > 0) {
                map.remove(EXPIRED_KEY)
            }
            for (days in snapshot.expireDays) {
                if (remaining > days * DAY_MILLIS) {
                    map.remove(expireKey(days))
                }
            }

            val passed = mutableListOf<Pair<String, SubscriptionAlert?>>()
            if (remaining <= 0) {
                passed += EXPIRED_KEY to SubscriptionAlert.Expired
            }
            for (days in snapshot.expireDays.sorted()) {
                if (remaining > 0 && remaining <= days * DAY_MILLIS) {
                    passed += expireKey(days) to SubscriptionAlert.ExpiresIn(days)
                } else if (remaining <= 0) {
                    passed += expireKey(days) to null
                }
            }

            // At most one alert per category per pass: a subscription that sat
            // untouched past every threshold at once (first run after a long
            // gap) should say "expired" once, not "7 days / 3 days / 1 day /
            // expired" back to back.
            var shown = false
            for ((key, alert) in passed) {
                if (!map.containsKey(key)) {
                    if (!shown && alert != null) {
                        alerts += alert
                        shown = true
                    }
                    map[key] = nowMillis
                } else if (alert != null) {
                    shown = true
                }
            }
        } else if (snapshot.expireAt == 0L) {
            map.keys.retainAll {
                !it.startsWith("expire_") && it != EXPIRED_KEY && it != EXPIRE_BASE_KEY
            }
        }

        if (snapshot.total > 0 && snapshot.trafficPercent.isNotEmpty()) {
            val used = snapshot.used.coerceAtLeast(0)
            val reached = { threshold: Int -> percentReached(used, snapshot.total, threshold) }

            for (threshold in snapshot.trafficPercent) {
                if (!reached(threshold)) {
                    map.remove(trafficKey(threshold))
                }
            }

            var shown = false
            for (threshold in snapshot.trafficPercent.filter { reached(it) }.sortedDescending()) {
                val key = trafficKey(threshold)
                if (!map.containsKey(key)) {
                    if (!shown) {
                        alerts += SubscriptionAlert.TrafficUsed(threshold)
                        shown = true
                    }
                    map[key] = nowMillis
                } else {
                    shown = true
                }
            }
        }

        return Outcome(alerts = alerts, notified = map)
    }

    private fun percentReached(used: Long, total: Long, threshold: Int): Boolean {
        return usedPercent(used, total)?.let { it >= threshold } ?: false
    }

    /**
     * Percent of the quota used right now, rounded down, or null when the quota
     * is unknown. A notification or dialog says this instead of the threshold
     * that fired: by the time it is read, the traffic is a few percent past it.
     */
    fun usedPercent(used: Long, total: Long): Int? {
        if (total <= 0) return null

        val spent = used.coerceAtLeast(0)
        val whole = spent / total * 100
        val rest = spent % total * 100 / total

        return (whole + rest).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    /**
     * Splits the time left into the units the text is worded in: from 3 days
     * on only days ("5 d"), from 1 to 3 days days and hours, under a day
     * hours and minutes, under an hour minutes. [remainingMillis] must be
     * positive — an expired subscription is worded as "expired", not as a
     * duration.
     */
    fun splitRemaining(remainingMillis: Long): SubscriptionRemaining {
        val days = remainingMillis / DAY_MILLIS
        if (days >= 3) return SubscriptionRemaining.Days(days.toInt())

        if (days >= 1) {
            val hours = remainingMillis % DAY_MILLIS / HOUR_MILLIS
            return SubscriptionRemaining.DaysHours(days.toInt(), hours.toInt())
        }

        val hours = remainingMillis / HOUR_MILLIS
        if (hours >= 1) {
            val minutes = remainingMillis % HOUR_MILLIS / MINUTE_MILLIS
            return SubscriptionRemaining.HoursMinutes(hours.toInt(), minutes.toInt())
        }

        return SubscriptionRemaining.Minutes((remainingMillis / MINUTE_MILLIS).coerceAtLeast(1).toInt())
    }

    /**
     * The notification slot of one alert kind of one profile. Per profile, so
     * two profiles expiring on the same day each keep their own notification
     * (and their own tap target — the PendingIntent request code is this id)
     * instead of the second one overwriting the first; per kind, so a fresh
     * "expires in 3 days" replaces a stale "expires in 7 days" while the
     * unrelated "traffic used" alert of the same profile stays.
     */
    fun notificationId(uuid: UUID, alert: SubscriptionAlert): Int {
        val slot = when (alert) {
            is SubscriptionAlert.ExpiresIn -> 1
            is SubscriptionAlert.Expired -> 2
            is SubscriptionAlert.TrafficUsed -> 3
        }

        return Objects.hash(uuid, slot)
    }
}
