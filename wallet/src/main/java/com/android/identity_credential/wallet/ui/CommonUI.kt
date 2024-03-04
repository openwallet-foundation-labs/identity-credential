@file:OptIn(ExperimentalMaterial3Api::class)

package com.android.identity_credential.wallet.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.android.identity_credential.wallet.R

/**
 * Presents a screen with an app bar on top and possibly a navigation icon in the top left corner.
 */
@Composable
fun ScreenWithAppBar(
    title: String,
    navigationIcon: @Composable () -> Unit,
    scrollable: Boolean = true,
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
            )
        },
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
    body: @Composable ColumnScope.() -> Unit,
) {
    ScreenWithAppBar(title, navigationIcon = {
        IconButton(onClick = { onBackButtonClick() }) {
            Icon(
                imageVector = Icons.Filled.ArrowBack,
                contentDescription = stringResource(R.string.accessibility_go_back_icon)
            )
        }
    }, scrollable = scrollable, body = body)
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
