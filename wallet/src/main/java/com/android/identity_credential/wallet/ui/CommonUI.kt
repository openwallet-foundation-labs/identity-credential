@file:OptIn(ExperimentalMaterial3Api::class)

package com.android.identity_credential.wallet.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavHostController

/**
 * Presents a screen with an app bar on top and possibly a navigation icon in the top left corner.
 */
@Composable
fun ScreenWithAppBar(
    title: String,
    navigationIcon: @Composable () -> Unit,
    body: @Composable ColumnScope.() -> Unit
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
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(innerPadding),
            verticalArrangement = Arrangement.Center,
            content = body
        )
    }
}

/** Presents a screen with an app bar on top and a back button in the top left corner. */
@Composable
fun ScreenWithAppBarAndBackButton(
    title: String,
    navigation: NavHostController,
    body: @Composable ColumnScope.() -> Unit
) {
    ScreenWithAppBar(title, navigationIcon = {
        IconButton(onClick = {
            navigation.popBackStack()
        }) {
            Icon(
                imageVector = Icons.Filled.ArrowBack, contentDescription = "Back Arrow"
            )
        }
    }, body = body)
}