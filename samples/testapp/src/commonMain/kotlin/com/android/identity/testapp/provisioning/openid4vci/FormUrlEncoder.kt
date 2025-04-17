package com.android.identity.testapp.provisioning.openid4vci

import io.ktor.http.encodeURLParameter

class FormUrlEncoder(block: FormUrlEncoder.() -> Unit) {
    private val buffer = StringBuilder()

    init {
        this.block()
    }

    fun add(name: String, value: String) {
        if (buffer.isNotEmpty()) {
            buffer.append("&")
        }
        buffer.append(encode(name))
        buffer.append("=")
        buffer.append(encode(value))
    }

    override fun toString(): String {
        return buffer.toString()
    }

    companion object {
        fun encode(text: String): String {
            return text.encodeURLParameter()
        }
    }
}