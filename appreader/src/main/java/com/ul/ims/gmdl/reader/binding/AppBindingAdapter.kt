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

package com.ul.ims.gmdl.reader.binding

import android.graphics.Bitmap
import android.icu.text.SimpleDateFormat
import android.icu.util.Calendar
import android.widget.ImageView
import android.widget.TextView
import androidx.databinding.BindingAdapter
import com.ul.ims.gmdl.cbordata.drivingPrivileges.DrivingPrivileges
import com.ul.ims.gmdl.cbordata.utils.DateUtils
import com.ul.ims.gmdl.reader.R
import java.util.*

@BindingAdapter("userImage")
fun setUserImage(imageView: ImageView, userImage: Bitmap?) {
    userImage?.let {
        imageView.setImageBitmap(it)
    }
}

@BindingAdapter("qrcode")
fun setQrcode(imageView: ImageView, qrcode: Bitmap?) {
    qrcode?.let {
        imageView.setImageBitmap(it)
    }
}

@BindingAdapter("dateFormat")
fun setDateFormat(textView: TextView, date: Calendar?) {
    date?.let {
        textView.text = DateUtils.getFormattedDateTime(date, DateUtils.DISPLAY_FORMAT_DATE)
    }
}

@BindingAdapter("dateTimeFormat")
fun setDateTimeFormat(textView: TextView, dateTime: Calendar?) {
    dateTime?.let {
        textView.text = DateUtils.getFormattedDateTime(dateTime, DateUtils.DISPLAY_FORMAT_DATE_TIME)
    }
}

@BindingAdapter("categories")
fun setCategories(textView: TextView, privileges: DrivingPrivileges?) {
    var text = ""
    val df = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    privileges?.let { priv ->
        for ((count, drivingPrivilege) in priv.drivingPrivileges.withIndex()) {
            if (count > 0) {
                text += "\n\n"
            }
            text += "Vehicle Category: ${drivingPrivilege.vehicleCategory}"
            drivingPrivilege.issueDate?.let {
                text += "\n    Issue Date: ${df.format(it)}"
            }
            drivingPrivilege.expiryData?.let {
                text += "\n    Expire Date: ${df.format(it)}"
            }
        }
    }
    textView.text = text
}

@BindingAdapter("issuerAuthVal")
fun setissuerAuthVal(imageView: ImageView, status: Boolean?) {
    status?.let {
        var resId = android.R.drawable.ic_delete

        if (it) {
            resId = R.drawable.ic_baseline_done
        }

        imageView.setImageResource(resId)
    }
}