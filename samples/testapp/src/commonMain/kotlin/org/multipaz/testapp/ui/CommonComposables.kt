package org.multipaz.testapp.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun GroupDivider() {
    Spacer(modifier = Modifier.height(16.dp))
    HorizontalDivider(color = Color.Gray, thickness = 1.dp)
    Spacer(modifier = Modifier.height(16.dp))
}