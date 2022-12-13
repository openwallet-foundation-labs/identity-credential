package com.android.mdl.appreader.util

import android.util.Log
import com.android.mdl.appreader.VerifierApp

fun Any.logDebug(message: String, exception: Throwable? = null) {
    if (!VerifierApp.isDebugLogEnabled()) return
    val tag: String = tagValue()
    if (exception == null) {
        Log.d(tag, message)
    } else {
        Log.d(tag, message, exception)
    }
}

fun Any.logError(message: String, exception: Throwable? = null) {
    if (!VerifierApp.isDebugLogEnabled()) return
    val tag: String = tagValue()
    if (exception == null) {
        Log.e(tag, message)
    } else {
        Log.e(tag, message, exception)
    }
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
