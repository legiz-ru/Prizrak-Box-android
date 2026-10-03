package com.github.kr328.clash.service.util

/**
 * The User-Agent of profile requests:
 * `prizrak-box/{versionName} (Android Build; Prizrak-Core {coreVersion})`.
 *
 * The app version stands right after `prizrak-box/`: the Remnawave panel
 * recognises an "extended" client by the `^prizrak-box/` prefix and only then
 * sends `serverDescription`, so the slash and a version are always present
 * ("unknown" when the version is not known). The core part is left out when
 * its version is blank.
 */
fun buildUserAgent(versionName: String?, coreVersion: String): String {
    val version = versionName?.takeIf { it.isNotBlank() } ?: "unknown"
    val core = coreVersion.takeIf { it.isNotBlank() }?.let { "; Prizrak-Core $it" } ?: ""

    return "prizrak-box/$version (Android Build$core)"
}
