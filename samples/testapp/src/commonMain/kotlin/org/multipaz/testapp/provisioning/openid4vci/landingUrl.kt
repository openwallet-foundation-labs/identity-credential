package org.multipaz.testapp.provisioning.openid4vci

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.multipaz.util.Logger
import org.multipaz.util.toBase64Url
import kotlin.random.Random

private const val TAG = "LangingUrl"

private val lock = Mutex()
val callbacks = mutableMapOf<String, SendChannel<String>>()

const val baseUrl = "https://sorotokin.com/multipaz/landing/"

private fun createUniqueUrl(): String {
    while (true) {
        val provisionalUrl = baseUrl + Random.Default.nextBytes(15).toBase64Url()
        if (!callbacks.contains(provisionalUrl)) {
            return provisionalUrl
        }
    }
}

suspend fun waitForNavigation(block: suspend (landingUrl: String) -> Unit): String {
    val channel = Channel<String>()
    val ladingUrl = lock.withLock {
        val url = createUniqueUrl()
        callbacks[url] = channel
        url
    }
    block(ladingUrl)
    return channel.receive()
}

suspend fun landingUrlNavigated(url: String) {
    val index = url.indexOf('?')
    val base = url.substring(0, index)
    val channel = lock.withLock {
        callbacks.remove(base)
    }
    if (channel == null) {
        Logger.w(TAG, "No callback registered for url '$url'")
    } else {
        Logger.w(TAG, "Handling callback for url '$url'")
        channel.send(url.substring(index + 1))
    }
}