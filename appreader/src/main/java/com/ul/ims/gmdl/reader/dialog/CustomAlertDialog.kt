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

package com.ul.ims.gmdl.reader.dialog

import android.content.Context
import androidx.appcompat.app.AlertDialog

class CustomAlertDialog(
    private val context: Context,
    val onDismissAction: () -> Unit
) {
    private var errorDialog: AlertDialog? = null

    private fun createErrorDialog(title: String, errorMsg: String?): AlertDialog {
        val builder: AlertDialog.Builder? = this.let {
            AlertDialog.Builder(context)
        }
        builder?.let {
            it.setCancelable(true)
            it.setMessage(errorMsg)
                .setTitle(title)
                .setCancelable(true)
                .apply {
                    setPositiveButton(
                        context.getString(android.R.string.ok)
                    ) { dialog, _ ->
                        dialog.dismiss()
                    }
                    setOnDismissListener {
                        onDismissAction()
                    }
                }
            return it.create()
        } ?: run {
            // if activity if null then we shouldn't be here as well
            throw RuntimeException("context is null when creating an error dialog")
        }
    }

    fun showErrorDialog(title: String, errorMsg: String?) {
        errorDialog = createErrorDialog(title, errorMsg)
        errorDialog?.show()
    }
}