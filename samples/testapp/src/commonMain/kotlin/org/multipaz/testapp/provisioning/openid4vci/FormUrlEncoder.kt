package org.multipaz.testapp.provisioning.openid4vci

import io.ktor.http.decodeURLQueryComponent
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

        fun parse(query: String): Map<String, String> {
            return buildMap {
                val parameters = query.substring(query.indexOf('?') + 1)
                for (parameter in parameters.split('&')) {
                    val index = parameter.indexOf('=')
                    if (index < 0) {
                        put(parameter.decodeURLQueryComponent(), "")
                    } else {
                        put(
                            key = parameter.substring(0, index).decodeURLQueryComponent(),
                            value = parameter.substring(index + 1).decodeURLQueryComponent()
                        )
                    }
                }
            }
        }
    }
}