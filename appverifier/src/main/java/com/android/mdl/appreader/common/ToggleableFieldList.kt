package com.android.mdl.appreader.common

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Composable
fun ToggleableFieldList(
    fields: List<String>,
    enabledStates: List<Boolean>,
    onToggle: (Int, Boolean) -> Unit
) {
    val scrollState = rememberScrollState()

    Box( // Wrap the Card with a Box so we can overlay the fade
        modifier = Modifier
            .padding(16.dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
            colors = CardDefaults.cardColors(
                contentColor = if (isSystemInDarkTheme()) Color.White else Color.Black
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(16.dp)
            ) {
                fields.forEachIndexed { index, field ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(
                            modifier = Modifier.animateContentSize(
                                animationSpec = tween(durationMillis = 200)
                            ),
                            onClick = { onToggle(index, !enabledStates[index]) },
                            label = {
                                Text(text = field, color = MaterialTheme.colorScheme.primary)
                            },
                            selected = enabledStates[index],
                            leadingIcon = if (enabledStates[index]) {
                                {
                                    Icon(
                                        imageVector = Icons.Filled.Done,
                                        contentDescription = field,
                                        modifier = Modifier.size(FilterChipDefaults.IconSize)
                                    )
                                }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }

        // Fade at the bottom
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(12.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, MaterialTheme.colorScheme.surface)
                    )
                )
        )
    }
}