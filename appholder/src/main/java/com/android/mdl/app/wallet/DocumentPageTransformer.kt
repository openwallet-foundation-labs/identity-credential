package com.android.mdl.app.wallet

import android.content.Context
import android.view.View
import androidx.viewpager2.widget.ViewPager2
import com.android.mdl.app.R
import kotlin.math.abs

class DocumentPageTransformer(context: Context) : ViewPager2.PageTransformer {
    private val resources = context.resources
    private val nextItemVisiblePx = resources.getDimension(R.dimen.viewpager_next_item_visible)
    private val currentItemHorizontalMarginPx = resources.getDimension(R.dimen.viewpager_current_item_horizontal_margin)
    private val pageTranslationX = nextItemVisiblePx + currentItemHorizontalMarginPx

    override fun transformPage(page: View, position: Float) {
        page.translationX = -pageTranslationX * position
        page.scaleY = 1 - (0.25f * abs(position))
    }
}