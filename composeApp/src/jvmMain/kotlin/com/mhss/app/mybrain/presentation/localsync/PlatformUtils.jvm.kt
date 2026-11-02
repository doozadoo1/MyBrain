package com.mhss.app.mybrain.presentation.localsync

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap

actual class KmpBitmap(val image: ImageBitmap)

@Composable
actual fun RequestLocalNetworkPermission(onGranted: () -> Unit) {
    // Desktop has no runtime local-network permission model.
    LaunchedEffect(Unit) { onGranted() }
}

@Composable
actual fun KmpImage(
    bitmap: KmpBitmap,
    contentDescription: String?,
    modifier: Modifier
) {
    Image(
        bitmap = bitmap.image,
        contentDescription = contentDescription,
        modifier = modifier
    )
}

@Composable
actual fun rememberQrScanLauncher(onResult: (KmpBitmap?) -> Unit): () -> Unit {
    // Camera scanning is not applicable on desktop; pair by manual IP instead.
    return { onResult(null) }
}
