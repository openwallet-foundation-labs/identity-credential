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

package com.ul.ims.gmdl.dialog

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.ul.ims.gmdl.R

class ConsentDialog(private val consentItems : List<String>,
                    val onConsent : (Map<String, Boolean>) -> Unit,
                    val onCancel : () -> Unit) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let { activity ->
            val selectedItems = HashMap<String, Boolean>()

            consentItems.forEach {
                selectedItems[it] = true
            }

            val builder = AlertDialog.Builder(activity)
            // Set the dialog title
            builder.setTitle(R.string.user_consent_dialog)
                // Specify the list array, the items to be selected by default (null for none),
                // and the listener through which to receive callbacks when items are selected
                .setMultiChoiceItems(getConsentLabels(consentItems, activity.applicationContext),
                    selectedItems.values.map { it }.toBooleanArray()
                ) { _, which, isChecked ->
                    selectedItems[consentItems[which]] = isChecked
                }
                // Set the action buttons
                .setPositiveButton(
                    android.R.string.ok
                ) { _, _ ->
                    onConsent(selectedItems)
                }
                .setNegativeButton(
                    android.R.string.cancel
                ) { _, _ ->
                    onCancel()
                }

            builder.create()
        } ?: throw IllegalStateException("Activity cannot be null")
    }

    private fun getConsentLabels(consentItems : List<String>, context: Context) : Array<String> {
        val labels = ArrayList<String>()

        consentItems.forEach {
            labels.add(context.getString(getLabel(it)))
        }

        return labels.toTypedArray()
    }

    private fun getLabel(item : String) : Int {
        return when (item) {
            "family_name" -> R.string.family_name
            "given_name" -> R.string.given_name
            "birth_date" -> R.string.date_of_birth
            "issue_date" -> R.string.date_of_issue
            "expiry_date" -> R.string.date_of_expiry
            "issuing_country" -> R.string.issuing_country
            "issuing_authority" -> R.string.issuing_authority
            "document_number" -> R.string.license_number
            "driving_privileges" -> R.string.categories_of_vehicles
            else -> 0
        }
    }
}
