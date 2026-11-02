package com.mhss.app.util.clipboard

import androidx.compose.ui.platform.Clipboard
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

actual suspend fun Clipboard.copyText(label: String, text: String) {
    // Bypass the Compose abstraction: the AWT clipboard is reliable on all
    // desktop platforms (headless-safe once the window is up).
    runCatching {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
    }
}

actual suspend fun Clipboard.pasteText(): String? {
    return try {
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        clipboard.getContents(null)?.let { contents ->
            if (contents.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                contents.getTransferData(DataFlavor.stringFlavor) as? String
            } else {
                null
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
