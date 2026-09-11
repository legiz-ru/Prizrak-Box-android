package com.github.kr328.clash.util

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter

/**
 * Renders [content] as a square black-on-white QR bitmap, [size] px on a side.
 * Shared by TvImportActivity (phone -> TV profile transfer) and the TV
 * URL-to-QR dialog (TV -> phone: hand off a link neither remote nor a TV
 * keyboard is a sane way to type).
 */
fun generateQrCode(content: String, size: Int): Bitmap {
    val hints = mapOf(EncodeHintType.MARGIN to 1)
    val matrix = MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    for (x in 0 until size) {
        for (y in 0 until size) {
            bmp.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
        }
    }
    return bmp
}
