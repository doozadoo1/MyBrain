package com.mhss.app.ui.components.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mhss.app.ui.Res
import com.mhss.app.ui.ic_search
import com.mhss.app.ui.note_may_be_outdated
import com.mhss.app.ui.semantic_results
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Plain data carrier so core/ui does not depend on the RAG data module. */
data class SemanticSearchItem(
    val id: String,
    val title: String,
    val text: String,
    val date: String,
    val stale: Boolean
)

@Composable
fun SemanticResultsHeader(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_search),
            contentDescription = null,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(Res.string.semantic_results),
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
fun SemanticSearchItemCard(
    item: SemanticSearchItem,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick(item.id) },
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(
                text = item.title.ifBlank { item.text.take(60) },
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (item.date.isNotBlank()) {
                Text(
                    text = item.date,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.size(4.dp))
            Text(
                text = item.text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            if (item.stale) {
                Spacer(Modifier.size(4.dp))
                Text(
                    text = stringResource(Res.string.note_may_be_outdated),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun HorizontalDividerSmall(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    )
}
