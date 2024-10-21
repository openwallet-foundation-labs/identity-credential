@file:OptIn(ExperimentalMaterial3Api::class)

package com.android.identity_credential.wallet.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.identity_credential.wallet.R

/**
 * Presents a screen with an app bar on top and possibly a navigation icon in the top left corner.
 */
@Composable
fun ScreenWithAppBar(
    title: String,
    navigationIcon: @Composable () -> Unit,
    scrollable: Boolean = true,
    actions: @Composable() (RowScope.() -> Unit) = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    body: @Composable ColumnScope.() -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                ),
                title = {
                    Text(
                        title, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = navigationIcon,
                scrollBehavior = scrollBehavior,
                actions = actions,
            )
        },
        snackbarHost = snackbarHost,
        floatingActionButton = floatingActionButton
    ) { innerPadding ->
        var modifier = Modifier
            .fillMaxHeight()
            .padding(innerPadding)
        if (scrollable) {
            modifier = modifier.verticalScroll(rememberScrollState())
        }
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.Center,
            content = body
        )
    }
}

/** Presents a screen with an app bar on top and a back button in the top left corner. */
@Composable
fun ScreenWithAppBarAndBackButton(
    title: String,
    onBackButtonClick: () -> Unit,
    scrollable: Boolean = true,
    actions: @Composable() (RowScope.() -> Unit) = {},
    snackbarHost: @Composable () -> Unit = {},
    body: @Composable ColumnScope.() -> Unit,
) {
    ScreenWithAppBar(
        title,
        snackbarHost = snackbarHost,
        navigationIcon = {
        IconButton(onClick = { onBackButtonClick() }) {
            Icon(
                imageVector = Icons.Filled.ArrowBack,
                contentDescription = stringResource(R.string.accessibility_go_back_icon)
            )
        }
    }, scrollable = scrollable, actions = actions, body = body)
}

@Composable
fun KeyValuePairText(
    keyText: String,
    valueText: String
) {
    Column(
        Modifier
            .padding(8.dp)
            .fillMaxWidth()) {
        Text(
            text = keyText,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = valueText,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun ColumnWithPortrait(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Layout(
        modifier = modifier,
        content = content
    ) { measurables, constraints ->
        var height = 0
        var first = true
        val placeables = measurables.map { measurable ->
            val placeable = measurable.measure(constraints)
            if (first) {
                first = false
            } else {
                height += placeable.height
            }
            placeable
        }

        layout(constraints.maxWidth,
            maxOf(constraints.minHeight, minOf(constraints.maxHeight, height))) {
            var x = 0
            var y = 0
            var first = true
            var floatHeight = 0

            placeables.forEach { placeable ->
                placeable.placeRelative(x, y)
                if (first) {
                    x = placeable.width
                    floatHeight = placeable.height
                    first = false;
                } else {
                    y += placeable.height
                    if (y >= floatHeight) {
                        x = 0
                    }
                }
            }
        }
    }
}

@Composable
fun SettingToggle(
    modifier: Modifier = Modifier,
    title: String,
    subtitleOn: String? = null,
    subtitleOff: String? = null,
    isChecked: Boolean = false,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            if (subtitleOn != null && subtitleOff != null) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val subtitle = if (isChecked) subtitleOn else subtitleOff
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = title,
                    fontWeight = FontWeight.Normal,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        Switch(
            checked = isChecked,
            enabled = enabled,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
fun SettingString(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    onClicked: () -> Unit,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Column(
            modifier = Modifier.weight(1f)
                .clickable {
                    onClicked()
                }
        ) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SettingSectionSubtitle(
    modifier: Modifier = Modifier,
    title: String
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                modifier = Modifier.padding(top = 20.dp),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
