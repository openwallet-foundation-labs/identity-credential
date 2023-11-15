package com.android.identity.wallet.composables

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.with
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun NumberChanger(
    modifier: Modifier = Modifier,
    number: Int,
    onNumberChanged: (newValue: Int) -> Unit,
    counterTextStyle: TextStyle = MaterialTheme.typography.bodyLarge
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { onNumberChanged(number - 1) }) {
            Icon(imageVector = Icons.Default.Remove, contentDescription = null)
        }
        AnimatedContent(
            targetState = number,
            label = "",
            transitionSpec = {
                if (targetState > initialState) {
                    slideInVertically { -it } with slideOutVertically { it }
                } else {
                    slideInVertically { it } with slideOutVertically { -it }
                }
            }
        ) { count ->
            Text(
                text = "$count",
                textAlign = TextAlign.Center,
                style = counterTextStyle
            )
        }
        IconButton(onClick = { onNumberChanged(number + 1) }) {
            Icon(imageVector = Icons.Default.Add, contentDescription = null)
        }
    }
}