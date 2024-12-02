package com.android.identity.util

fun String.htmlEscape(): String {
    return replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
}