package com.github.kr328.clash.service.subscription

import android.content.Context
import android.text.format.DateFormat
import com.github.kr328.clash.service.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * What the subscription-alert dialog says: the headline, and — while the
 * subscription is still running — an extra line with the exact expiry moment.
 */
data class SubscriptionAlertTexts(val message: String, val detail: String? = null)

/**
 * "05.10.2026, 14:30" in the device's language and 12/24-hour setting. An
 * absolute moment never goes stale the way "in 4 days" does while a
 * notification sits in the shade.
 */
fun Context.formatSubscriptionExpireDate(expireAtMillis: Long): String {
    val locale = Locale.getDefault()
    val skeleton = if (DateFormat.is24HourFormat(this)) "yMMddHHmm" else "yMMddhmma"

    return SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
        .format(Date(expireAtMillis))
}

private fun Context.remainingText(remaining: SubscriptionRemaining): String = when (remaining) {
    is SubscriptionRemaining.Days ->
        getString(R.string.subscription_remaining_days, remaining.days)
    is SubscriptionRemaining.DaysHours ->
        getString(R.string.subscription_remaining_days_hours, remaining.days, remaining.hours)
    is SubscriptionRemaining.HoursMinutes ->
        getString(R.string.subscription_remaining_hours_minutes, remaining.hours, remaining.minutes)
    is SubscriptionRemaining.Minutes ->
        getString(R.string.subscription_remaining_minutes, remaining.minutes)
}

/** The wording of the threshold that fired — only for a profile whose expiry is unknown. */
private fun Context.thresholdText(alert: SubscriptionAlert): String = when (alert) {
    is SubscriptionAlert.Expired -> getString(R.string.subscription_expired)
    is SubscriptionAlert.ExpiresIn -> resources.getQuantityString(
        R.plurals.subscription_expires_in_days,
        alert.days,
        alert.days,
    )
    is SubscriptionAlert.TrafficUsed -> getString(R.string.subscription_traffic_used, alert.percent)
}

/**
 * The title of the notification. A notification is frozen when it is posted,
 * so it carries the exact expiry moment instead of "in N days", and the
 * traffic percent measured at that moment instead of the threshold's.
 *
 * [expireAtMillis] is 0 when the subscription has no expiry.
 */
fun Context.subscriptionAlertTitle(
    alert: SubscriptionAlert,
    expireAtMillis: Long,
    total: Long,
    used: Long,
): String = when (alert) {
    is SubscriptionAlert.Expired -> getString(R.string.subscription_expired)
    is SubscriptionAlert.ExpiresIn ->
        if (expireAtMillis != 0L) {
            getString(R.string.subscription_expires_at, formatSubscriptionExpireDate(expireAtMillis))
        } else {
            thresholdText(alert)
        }
    is SubscriptionAlert.TrafficUsed ->
        getString(
            R.string.subscription_traffic_used,
            SubscriptionAlerts.usedPercent(used, total) ?: alert.percent,
        )
}

/**
 * The text shown when a notification is opened: computed from the profile as
 * it is now, not from the threshold that raised the alert. The notification
 * may be hours or days old by then — a "4 days" that is really 3 days and a
 * few hours, or traffic that has crept from 80% to 83%.
 *
 * [nowMillis] must already carry the panel clock-skew correction.
 */
fun Context.describeSubscriptionAlert(
    alert: SubscriptionAlert,
    expireAtMillis: Long,
    total: Long,
    used: Long,
    nowMillis: Long,
): SubscriptionAlertTexts {
    if (alert is SubscriptionAlert.TrafficUsed) {
        return SubscriptionAlertTexts(subscriptionAlertTitle(alert, expireAtMillis, total, used))
    }

    if (expireAtMillis == 0L) {
        return SubscriptionAlertTexts(thresholdText(alert))
    }

    val remaining = expireAtMillis - nowMillis

    if (remaining <= 0) {
        return SubscriptionAlertTexts(getString(R.string.subscription_expired))
    }

    return SubscriptionAlertTexts(
        message = remainingText(SubscriptionAlerts.splitRemaining(remaining)),
        detail = getString(R.string.subscription_expires_on, formatSubscriptionExpireDate(expireAtMillis)),
    )
}
