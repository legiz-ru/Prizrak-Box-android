package com.github.kr328.clash.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.getSystemService
import com.github.kr328.clash.common.util.TvUtils
import com.github.kr328.clash.design.R
import kotlinx.coroutines.flow.MutableStateFlow

/** State for [UrlQrDialog][com.github.kr328.clash.design.compose.component.UrlQrDialog]. */
data class UrlQrDialogState(
    val title: String,
    val url: String,
    val qr: Bitmap,
)

/**
 * Opens a subscription-related link (renew/support/provider-website), the
 * same way regardless of which of the three screens (Main, Profiles,
 * Properties) it was tapped from.
 *
 * On a normal device this is just `ACTION_VIEW`, as it always was. On TV
 * (`FEATURE_LEANBACK`) that's a bad default: a D-pad-typed URL bar, or no
 * browser at all on many boxes. Show a QR code instead so the link can be
 * picked up on a phone, with the raw URL alongside to copy for anyone near
 * a keyboard instead of a camera.
 */
class UrlOpener(private val activity: ComponentActivity) {
    val dialogState = MutableStateFlow<UrlQrDialogState?>(null)

    fun open(url: String, title: String) {
        if (url.isEmpty()) return

        if (TvUtils.isTv(activity)) {
            dialogState.value = UrlQrDialogState(title, url, generateQrCode(url, 512))
            return
        }

        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
        }
    }

    fun dismiss() {
        dialogState.value = null
    }

    fun copyLink() {
        val url = dialogState.value?.url ?: return

        activity.getSystemService<ClipboardManager>()
            ?.setPrimaryClip(ClipData.newPlainText("url", url))

        Toast.makeText(activity, R.string.tv_url_qr_copied, Toast.LENGTH_SHORT).show()
    }
}
