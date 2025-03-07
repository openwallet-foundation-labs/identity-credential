package org.multipaz_credential.wallet

import android.graphics.Bitmap

sealed class AttributeDisplayInfo

data class AttributeDisplayInfoPlainText(val name: String, val value: String) : AttributeDisplayInfo()
data class AttributeDisplayInfoHtml(val name: String, val value: String) : AttributeDisplayInfo()
data class AttributeDisplayInfoImage(val name: String, val image: Bitmap) : AttributeDisplayInfo()
