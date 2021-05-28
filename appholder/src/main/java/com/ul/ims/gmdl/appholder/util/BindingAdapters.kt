package com.ul.ims.gmdl.appholder.util

import android.view.View
import android.widget.LinearLayout
import androidx.databinding.BindingAdapter

object BindingAdapters {
    /**
     * A Binding Adapter that is called whenever the value of the attribute `app:engagementView`
     * changes. Receives a view with the QR Code for the device engagement.
     */
    @BindingAdapter("app:engagementView")
    @JvmStatic
    fun engagementView(view: LinearLayout, viewEngagement: View?) {
        viewEngagement?.let {
            view.addView(it)
        }
    }
}