package com.github.kr328.clash.util

import android.app.Activity
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings

/**
 * MIUI / HyperOS keep their own "Autostart" switch per app, outside Android's
 * permissions: without it the system does not deliver BOOT_COMPLETED to the
 * app, and "Auto restart" does nothing after a reboot. There is no API to turn
 * it on, so the app reads it where it can and takes the user to the system
 * screen that owns it.
 */
object XiaomiAutoStart {
    enum class State { Enabled, Disabled, Unknown }

    /** MIUI's private app op for autostart, by number and by name. */
    private const val OP_AUTO_START = 10008
    private const val OP_AUTO_START_NAME = "auto_start"
    private const val SECURITY_CENTER = "com.miui.securitycenter"

    /** A Xiaomi, Redmi or POCO device, or any ROM that says it is MIUI / HyperOS. */
    val isXiaomi: Boolean by lazy {
        val brands = setOf("xiaomi", "redmi", "poco")
        Build.MANUFACTURER.lowercase() in brands ||
            Build.BRAND.lowercase() in brands ||
            systemProperty("ro.miui.ui.version.name").isNotEmpty() ||
            systemProperty("ro.mi.os.version.name").isNotEmpty()
    }

    /** The switch's state; [State.Unknown] where the ROM does not tell (never a guessed "off"). */
    fun state(context: Context): State {
        if (!isXiaomi) return State.Unknown
        return miuiHelperState(context) ?: appOpState(context)
    }

    /**
     * The system screen with the switch: MIUI's own autostart page for this
     * app, its list of apps, else the app's details. False when none opened.
     */
    fun openSettings(context: Context): Boolean {
        if (!isXiaomi) return false
        val pkg = context.packageName
        return listOf(
            Intent("miui.intent.action.OP_AUTO_START")
                .setPackage(SECURITY_CENTER)
                .addCategory(Intent.CATEGORY_DEFAULT)
                .putExtra("extra_pkgname", pkg),
            Intent().setComponent(
                ComponentName(SECURITY_CENTER, "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            ),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")),
        ).any { intent ->
            runCatching {
                if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }.isSuccess
        }
    }

    /** MIUI's hidden helper: 0 allowed, 1 denied. */
    private fun miuiHelperState(context: Context): State? = runCatching {
        val method = Class.forName("android.miui.AppOpsUtils")
            .getDeclaredMethod("getApplicationAutoStart", Context::class.java, String::class.java)
            .apply { isAccessible = true }
        when (method.invoke(null, context, context.packageName) as? Int) {
            0 -> State.Enabled
            1 -> State.Disabled
            else -> null
        }
    }.getOrNull()

    /** Where the helper is gone: the app op itself, by its number, else by its name. */
    private fun appOpState(context: Context): State = runCatching {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = runCatching {
            AppOpsManager::class.java
                .getMethod("checkOpNoThrow", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, String::class.java)
                .invoke(appOps, OP_AUTO_START, Process.myUid(), context.packageName) as Int
        }.getOrElse {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(OP_AUTO_START_NAME, Process.myUid(), context.packageName)
        }
        when (mode) {
            AppOpsManager.MODE_ALLOWED -> State.Enabled
            AppOpsManager.MODE_IGNORED -> State.Disabled
            else -> State.Unknown
        }
    }.getOrDefault(State.Unknown)

    private fun systemProperty(key: String): String = runCatching {
        Class.forName("android.os.SystemProperties")
            .getMethod("get", String::class.java)
            .invoke(null, key) as? String
    }.getOrNull().orEmpty()
}
