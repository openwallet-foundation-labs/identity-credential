package com.android.identity.kmmtestapp

import androidx.compose.ui.window.ComposeUIViewController

private val app = App()

fun MainViewController() = ComposeUIViewController {
    app.Content()
}
