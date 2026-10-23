package com.github.kr328.clash.design.store

import android.content.Context
import com.github.kr328.clash.service.model.Profile
import java.util.UUID

/**
 * The order of Home's deck, nearest the active card first, as on iOS: a
 * profile left behind goes nearest; a drag in the deck sets the whole order.
 * Kept by the app only.
 */
object ProfileRecency {
    private const val PREFERENCES = "ui"
    private const val ORDER = "recent_profiles"
    private const val LAST_ACTIVE = "recent_profiles_last_active"
    private const val LIMIT = 500

    private fun prefs(context: Context) = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun ids(context: Context): List<UUID> =
        prefs(context).getString(ORDER, "").orEmpty()
            .split(',')
            .mapNotNull { runCatching { UUID.fromString(it) }.getOrNull() }

    /**
     * The active profile is [active] now: the one active before it, if
     * another, goes nearest the card. Called whenever the active profile is
     * read, so a change made anywhere is noticed.
     */
    fun noteActive(context: Context, active: UUID?) {
        val prefs = prefs(context)
        val previous = prefs.getString(LAST_ACTIVE, null)?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        if (active == null || previous == active) {
            if (active != null && previous == null) prefs.edit().putString(LAST_ACTIVE, active.toString()).apply()
            return
        }
        val list = ids(context).filter { it != previous && it != active }.toMutableList()
        if (previous != null) list.add(0, previous)
        prefs.edit()
            .putString(ORDER, list.take(LIMIT).joinToString(","))
            .putString(LAST_ACTIVE, active.toString())
            .apply()
    }

    /** The deck rearranged by hand, nearest the active card first. */
    fun set(context: Context, nearestFirst: List<UUID>) {
        prefs(context).edit().putString(ORDER, nearestFirst.take(LIMIT).joinToString(",")).apply()
    }

    /** The other profiles, the one used last first; those never left behind follow in the list's order. */
    fun ordered(context: Context, profiles: List<Profile>): List<Profile> {
        val rank = HashMap<UUID, Int>()
        ids(context).forEachIndexed { index, id -> rank.putIfAbsent(id, index) }
        return profiles.withIndex()
            .sortedWith(compareBy({ rank[it.value.uuid] ?: Int.MAX_VALUE }, { it.index }))
            .map { it.value }
    }
}
