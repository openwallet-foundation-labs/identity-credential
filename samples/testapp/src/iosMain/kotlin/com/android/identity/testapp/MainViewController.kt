package org.multipaz.testapp

import androidx.compose.ui.window.ComposeUIViewController
import kotlinx.coroutines.runBlocking
import org.multipaz.prompt.IosPromptModel

private val app = App.getInstance(IosPromptModel())

fun MainViewController() = ComposeUIViewController {
    app.Content()
}

fun HandleUrl(url: String) = app.handleUrl(url)