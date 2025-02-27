package com.android.identity.testapp

import androidx.compose.ui.window.ComposeUIViewController
import com.android.identity.prompt.IosPromptModel

private val app = App.getInstanceAndInitializeInBackground(IosPromptModel())

fun MainViewController() = ComposeUIViewController {
    app.Content()
}