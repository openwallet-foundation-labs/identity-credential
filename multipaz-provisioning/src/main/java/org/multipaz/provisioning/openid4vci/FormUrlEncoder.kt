package org.multipaz.provisioning.openid4vci

import java.lang.StringBuilder
import java.net.URLEncoder

class FormUrlEncoder(block: FormUrlEncoder.() -> Unit) {
    private val buffer = StringBuilder()

    init {
        this.block()
    }

    fun add(name: String, value: String) {
        if (buffer.isNotEmpty()) {
            buffer.append("&")
        }
        buffer.append(URLEncoder.encode(name, "UTF-8"))
        buffer.append("=")
        buffer.append(URLEncoder.encode(value, "UTF-8"))
    }

    override fun toString(): String {
        return buffer.toString()
    }
}