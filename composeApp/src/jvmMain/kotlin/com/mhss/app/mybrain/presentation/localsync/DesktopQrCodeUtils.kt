package com.mhss.app.mybrain.presentation.localsync

import androidx.compose.ui.graphics.toComposeImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import org.koin.core.annotation.Single
import java.awt.image.BufferedImage

/**
 * Desktop QR utils: encoding uses zxing's pure-Java core; decoding needs a
 * camera feed which desktop pairing doesn't use (manual IP entry instead),
 * so decode always returns null.
 */
@Single
class DesktopQrCodeUtils : QrCodeUtils {

    override fun generateQrCode(content: String, size: Int): KmpBitmap {
        val matrix = MultiFormatWriter().encode(
            content,
            BarcodeFormat.QR_CODE,
            size,
            size,
            mapOf<EncodeHintType, Any?>(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L)
        )
        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_RGB).apply {
            val pixels = IntArray(size * size)
            for (y in 0 until size) {
                for (x in 0 until size) {
                    pixels[y * size + x] = if (matrix.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
                }
            }
            setRGB(0, 0, size, size, pixels, 0, size)
        }
        return KmpBitmap(image.toComposeImageBitmap())
    }

    override fun decodeQrFromBitmap(bitmap: KmpBitmap): String? {
        // No camera QR input on desktop.
        return null
    }
}
