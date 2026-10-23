package com.github.kr328.clash

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.setContent
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.compose.screen.ApkBrokenScreen
import kotlinx.coroutines.isActive

class ApkBrokenActivity : BaseActivity() {
    override suspend fun main() {
        setContent {
            AppTheme {
                ApkBrokenScreen(
                    onBack = { finish() },
                    onOpenReleases = {
                        startActivity(
                            Intent(Intent.ACTION_VIEW)
                                .setData(Uri.parse(getString(R.string.meta_github_url)))
                        )
                    },
                )
            }
        }

        while (isActive) {
            events.receive()
        }
    }
}
