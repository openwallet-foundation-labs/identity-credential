package com.android.identity.wallet.adapter

import android.icu.text.SimpleDateFormat
import android.widget.TextView
import androidx.databinding.BindingAdapter
import java.util.*

@BindingAdapter("displayDateTime")
fun bindDisplayDateTime(view: TextView, calendar: Calendar?) {
    calendar?.let {
        val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSXXX", Locale.getDefault())
        view.text = df.format(it.time)
    }
}