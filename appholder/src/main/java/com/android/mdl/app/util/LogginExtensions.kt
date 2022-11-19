package com.android.mdl.app.util

import android.util.Log

fun Any.log(message: String, exception: Throwable? = null) {
    val tag: String = tagValue()
    if (exception == null) {
        Log.d(tag, message)
    } else {
        Log.e(tag, message, exception)
    }
}

private fun Any.tagValue(): String {
    val fullClassName: String = this::class.qualifiedName ?: this::class.java.typeName
    val outerClassName = fullClassName.substringBefore('$')
    val simplerOuterClassName = outerClassName.substringAfterLast('.')
    return if (simplerOuterClassName.isEmpty()) {
        fullClassName
    } else {
        simplerOuterClassName.removeSuffix("Kt")
    }
}
