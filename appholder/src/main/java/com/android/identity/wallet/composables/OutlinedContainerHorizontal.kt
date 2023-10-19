package com.android.identity.wallet.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun OutlinedContainerHorizontal(
    modifier: Modifier = Modifier,
    outlineBorderWidth: Dp = 2.dp,
    outlineBrush: Brush? = null,
    content: @Composable RowScope.() -> Unit
) {
    val brush = outlineBrush ?: SolidColor(MaterialTheme.colorScheme.outline)
    Row(
        modifier = modifier
            .heightIn(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(outlineBorderWidth, brush, RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.inverseOnSurface),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            content()
        }
    }
}
