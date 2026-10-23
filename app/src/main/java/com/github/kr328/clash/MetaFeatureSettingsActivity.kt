package com.github.kr328.clash

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.setContent
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.compose.screen.MetaFeatureSettingsScreen
import com.github.kr328.clash.util.GetContentCompat
import com.github.kr328.clash.util.clashDir
import com.github.kr328.clash.util.withClash
import com.github.kr328.clash.design.compose.dialog.ExpressiveDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume

private enum class GeoImport { GeoIp, GeoSite, Country, ASN }

class MetaFeatureSettingsActivity : BaseActivity() {
    private val resetRequests = Channel<Unit>(Channel.CONFLATED)

    override suspend fun main() {
        val configuration = withClash { queryOverride(Clash.OverrideSlot.Persist) }

        defer {
            withClash {
                patchOverride(Clash.OverrideSlot.Persist, configuration)
            }
        }

        setContent {
            AppTheme {
                MetaFeatureSettingsScreen(
                    configuration = configuration,
                    onBack = { finish() },
                    onReset = { resetRequests.trySend(Unit) },
                    onImportGeoIp = { launchImport(GeoImport.GeoIp) },
                    onImportGeoSite = { launchImport(GeoImport.GeoSite) },
                    onImportCountry = { launchImport(GeoImport.Country) },
                    onImportASN = { launchImport(GeoImport.ASN) },
                )
            }
        }

        while (isActive) {
            select<Unit> {
                events.onReceive { }
                resetRequests.onReceive {
                    if (requestResetConfirm()) {
                        defer {
                            withClash {
                                clearOverride(Clash.OverrideSlot.Persist)
                            }
                        }
                        finish()
                    }
                }
            }
        }
    }

    private fun launchImport(type: GeoImport) {
        launch {
            val uri = startActivityForResult(GetContentCompat(), "*/*")
            importGeoFile(uri, type)
        }
    }

    private suspend fun requestResetConfirm(): Boolean {
        return suspendCancellableCoroutine { ctx ->
            val dialog = ExpressiveDialog.Builder(this)
                .setTitle(R.string.reset_override_settings)
                .setMessage(R.string.reset_override_settings_message)
                .setDestructiveButton(R.string.ok) { _, _ -> ctx.resume(true) }
                .setNegativeButton(R.string.cancel) { _, _ -> }
                .show()

            dialog.setOnDismissListener {
                if (!ctx.isCompleted) ctx.resume(false)
            }

            ctx.invokeOnCancellation { dialog.dismiss() }
        }
    }

    private val validDatabaseExtensions = listOf(
        ".metadb", ".db", ".dat", ".mmdb"
    )

    private suspend fun importGeoFile(uri: Uri?, importType: GeoImport) {
        val cursor: Cursor? = uri?.let {
            contentResolver.query(it, null, null, null, null, null)
        }
        cursor?.use {
            if (it.moveToFirst()) {
                val columnIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val displayName: String =
                    if (columnIndex != -1) it.getString(columnIndex) else ""
                val ext = "." + displayName.substringAfterLast(".")

                if (!validDatabaseExtensions.contains(ext)) {
                    ExpressiveDialog.Builder(this)
                        .setTitle(R.string.geofile_unknown_db_format)
                        .setMessage(
                            getString(
                                R.string.geofile_unknown_db_format_message,
                                validDatabaseExtensions.joinToString("/")
                            )
                        )
                        .setPositiveButton("OK") { _, _ -> }
                        .show()
                    return
                }
                val outputFileName = when (importType) {
                    GeoImport.GeoIp -> "geoip$ext"
                    GeoImport.GeoSite -> "geosite$ext"
                    GeoImport.Country -> "country$ext"
                    GeoImport.ASN -> "ASN$ext"
                    else -> ""
                }

                withContext(Dispatchers.IO) {
                    val outputFile = File(clashDir, outputFileName)
                    contentResolver.openInputStream(uri).use { ins ->
                        FileOutputStream(outputFile).use { outs ->
                            ins?.copyTo(outs)
                        }
                    }
                }
                Toast.makeText(
                    this, getString(R.string.geofile_imported, displayName),
                    Toast.LENGTH_LONG
                ).show()
                return
            }
        }
        Toast.makeText(this, R.string.geofile_import_failed, Toast.LENGTH_LONG).show()
    }
}
