package com.android.identity.testapp

import androidx.compose.ui.window.ComposeUIViewController
import kotlinx.coroutines.runBlocking
import platform.UIKit.UIViewController

private val app = App()

fun MainViewController(): UIViewController {
    runBlocking {
        app.init()
    }
    return ComposeUIViewController {
        app.Content()
    }
}
