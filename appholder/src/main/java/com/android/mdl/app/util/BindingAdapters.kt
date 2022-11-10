package com.android.mdl.app.util

import android.view.View
import android.view.ViewGroup
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
            (viewEngagement.parent as? ViewGroup)?.removeView(viewEngagement)
            view.addView(it)
        }
    }
}