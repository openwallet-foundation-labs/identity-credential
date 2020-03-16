/*
 * Copyright (C) 2019 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/

package com.ul.ims.gmdl.cbordata.interpreter

import android.util.Log
import co.nstant.`in`.cbor.CborDecoder
import co.nstant.`in`.cbor.CborException
import co.nstant.`in`.cbor.model.Map
import co.nstant.`in`.cbor.model.UnicodeString
import com.ul.ims.gmdl.cbordata.command.ICommand
import com.ul.ims.gmdl.cbordata.request.Request
import com.ul.ims.gmdl.cbordata.request.Request.Companion.DOC_REQUESTS_KEY
import com.ul.ims.gmdl.cbordata.response.Response
import com.ul.ims.gmdl.cbordata.response.Response.Companion.KEY_DOCUMENTS
import java.io.ByteArrayInputStream

class CborDataInterpreter : IDataInterpreter {
    companion object {
        val LOG_TAG = CborDataInterpreter::class.java.simpleName
    }
    override fun interpret(data: ByteArray?): ICommand? {
        var command : ICommand? = null
        try {
            data?.let {
                val bais = ByteArrayInputStream(data)
                val decoded = CborDecoder(bais).decode()
                if (decoded.size > 0) {
                    val structureItems: Map? = decoded[0] as? Map
                    structureItems?.let { struct ->
                        struct.keys.forEach {
                            val key = it as? UnicodeString
                            when (key?.string) {
                                DOC_REQUESTS_KEY -> {
                                    command = Request.Builder()
                                        .decode(data)
                                        .build()
                                    return command
                                }
                                KEY_DOCUMENTS -> {
                                    command = Response.Builder()
                                        .decode(data)
                                        .build()
                                    return command
                                }
                            }
                        }
                    }
                }
            }
        } catch (ex: CborException) {
            Log.e(LOG_TAG, "${ex.message}")
            Log.d(LOG_TAG, "Unrecognized command = ${data?.let { String(it) }}")
        }
        finally {
            return command
        }
    }
}