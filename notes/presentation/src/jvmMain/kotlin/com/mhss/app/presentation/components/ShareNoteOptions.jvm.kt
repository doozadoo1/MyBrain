package com.mhss.app.presentation.components

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.mhss.app.ui.Res
import com.mhss.app.ui.ic_plain_text
import com.mhss.app.ui.plain_text
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
actual fun ShareNoteAsPlainTextOption(
    title: String,
    content: String,
    onOptionSelected: () -> Unit,
) {
    // Desktop has no OS share sheet (Phase 1): sharing as plain text copies it
    // to the system clipboard.
    DropdownMenuItem(
        text = { Text(stringResource(Res.string.plain_text)) },
        onClick = {
            CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
                runCatching {
                    Toolkit.getDefaultToolkit()
                        .systemClipboard
                        .setContents(StringSelection("$title\n\n$content"), null)
                }
            }
            onOptionSelected()
        },
        leadingIcon = {
            Icon(
                painterResource(Res.drawable.ic_plain_text),
                contentDescription = stringResource(Res.string.plain_text)
            )
        }
    )
}
