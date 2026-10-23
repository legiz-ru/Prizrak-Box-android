package com.github.kr328.clash.util

import io.github.g00fy2.quickie.config.BarcodeFormat
import io.github.g00fy2.quickie.config.ScannerConfig

/**
 * The QR scanner's look everywhere it opens: ✕ in the top corner, as on iOS
 * (back closes it too, but the button is where people look), and the torch
 * opposite it. QR codes only.
 */
val qrScannerConfig: ScannerConfig = ScannerConfig.build {
    setBarcodeFormats(listOf(BarcodeFormat.FORMAT_QR_CODE))
    setShowCloseButton(true)
    setShowTorchToggle(true)
}
