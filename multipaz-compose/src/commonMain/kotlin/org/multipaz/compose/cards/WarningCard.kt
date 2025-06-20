package org.multipaz.compose.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * A composable which shows a card with a warning.
 *
 * @param modifier A Modifier.
 * @param content the content.
 */
@Composable
fun WarningCard(
    modifier: Modifier = Modifier,
    content: @Composable (() -> Unit)
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape = RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.errorContainer),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
        ) {
            Icon(
                modifier = Modifier.padding(end = 12.dp),
                imageVector = Icons.Filled.Warning,
                contentDescription = "An error icon",
                tint = MaterialTheme.colorScheme.onErrorContainer
            )

            CompositionLocalProvider(
                LocalContentColor provides MaterialTheme.colorScheme.onErrorContainer
            ) {
                content()
            }
        }
    }
}
