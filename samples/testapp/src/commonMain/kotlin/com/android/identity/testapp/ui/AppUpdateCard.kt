package com.android.identity.testapp.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import io.github.z4kn4fein.semver.Version
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.readBytes
import io.ktor.http.HttpStatusCode
import org.multipaz.compose.cards.InfoCard
import org.multipaz.testapp.BuildConfig
import org.multipaz.testapp.platformHttpClientEngineFactory
import org.multipaz.util.Logger

private const val TAG = "AppUpdateCard"

@Composable
fun AppUpdateCard() {
    if (BuildConfig.TEST_APP_UPDATE_URL.isEmpty()) {
        return
    }

    val latestVersionString = remember { mutableStateOf<String?>(null) }

    LaunchedEffect(true) {
        val httpClient = HttpClient(platformHttpClientEngineFactory().create())
        val response = httpClient.get(BuildConfig.TEST_APP_UPDATE_URL)
        if (response.status == HttpStatusCode.OK) {
            latestVersionString.value = response.readBytes().decodeToString().trim()
            Logger.i(TAG, "Latest available version from ${BuildConfig.TEST_APP_UPDATE_WEBSITE_URL} is ${latestVersionString.value}")
        }
    }


    latestVersionString.value?.let {
        val currentVersion = Version.parse(
            versionString = BuildConfig.VERSION,
            strict = false
        )
        val availableVersion = Version.parse(
            versionString = it,
            strict = false
        )
        if (currentVersion < availableVersion) {
            InfoCard {
                val str = buildAnnotatedString {
                    append(
                        "You are running version ${BuildConfig.VERSION} and version " +
                                "${latestVersionString.value} is the latest available on "
                    )
                    withLink(
                        LinkAnnotation.Url(
                            BuildConfig.TEST_APP_UPDATE_WEBSITE_URL,
                            TextLinkStyles(
                                style = SpanStyle(color = Color.Blue, textDecoration = TextDecoration.Underline),
                            )
                        )
                    ) {
                        append(BuildConfig.TEST_APP_UPDATE_WEBSITE_URL)
                    }
                    append(".")
                }
                Text(text = str)
            }
        }
    }
}