package com.github.kr328.clash.design.model

import android.graphics.drawable.Drawable
import com.github.kr328.clash.core.model.Connection

data class ConnectionGroup(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val connections: List<Connection>,
    /** Bytes per second now, from the last two snapshots (open connections only). */
    val uploadSpeed: Long,
    val downloadSpeed: Long,
    /** Bytes so far over the group's connections. */
    val uploadTotal: Long,
    val downloadTotal: Long,
    /** Open connections: the row shows the speed; closed ones show what they moved. */
    val live: Boolean,
)
