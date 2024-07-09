package com.android.identity.testapp

import androidx.compose.ui.window.ComposeUIViewController

private val app = App()

fun MainViewController() = ComposeUIViewController {
    app.Content()
}
