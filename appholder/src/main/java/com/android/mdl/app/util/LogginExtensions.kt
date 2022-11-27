package com.android.mdl.app.util

import android.util.Log

fun Any.log(message: String, exception: Throwable? = null) {
    if (!PreferencesHelper.isDebugLoggingEnabled()) return
    val tag: String = tagValue()
    if (exception == null) {
        Log.d(tag, message)
    } else {
        Log.e(tag, message, exception)
    }
}

fun Any.logInfo(message: String) {
    if (!PreferencesHelper.isDebugLoggingEnabled()) return
    val tag: String = tagValue()
    Log.i(tag, message)
}

fun Any.logWarning(message: String) {
    if (!PreferencesHelper.isDebugLoggingEnabled()) return
    val tag: String = tagValue()
    Log.w(tag, message)
}

fun Any.logError(message: String) {
    if (!PreferencesHelper.isDebugLoggingEnabled()) return
    val tag: String = tagValue()
    Log.e(tag, message)
}

private fun Any.tagValue(): String {
    if (this is String) return this
    val fullClassName: String = this::class.qualifiedName ?: this::class.java.typeName
    val outerClassName = fullClassName.substringBefore('$')
    val simplerOuterClassName = outerClassName.substringAfterLast('.')
    return if (simplerOuterClassName.isEmpty()) {
        fullClassName
    } else {
        simplerOuterClassName.removeSuffix("Kt")
    }
}
