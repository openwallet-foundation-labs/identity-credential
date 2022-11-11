package com.android.mdl.app.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import androidx.annotation.DrawableRes
import com.android.mdl.app.R

class ColorAdapter(
    context: Context,
) : BaseAdapter() {

    private val inflater = LayoutInflater.from(context)
    private val labels = context.resources.getStringArray(R.array.document_color)

    override fun getCount(): Int = labels.size

    override fun getItem(position: Int): String = labels[position]

    override fun getItemId(position: Int): Long = labels[position].hashCode().toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: inflater.inflate(R.layout.color_dropdown_item, null, true)
        view.findViewById<TextView>(R.id.label).text = getItem(position)
        view.findViewById<View>(R.id.color_preview).setBackgroundResource(backgroundFor(position))
        return view
    }

    @DrawableRes
    private fun backgroundFor(position: Int): Int {
        return when (position) {
            1 -> R.drawable.yellow_gradient
            2 -> R.drawable.blue_gradient
            else -> R.drawable.green_gradient
        }
    }
}