package com.android.identity.wallet.util

import android.graphics.Bitmap
import com.android.identity.documenttype.DocumentAttributeType
import com.android.identity.documenttype.IntegerOption
import com.android.identity.documenttype.StringOption

data class Field(
    val id: Int,
    val label: String,
    val name: String,
    val fieldType: DocumentAttributeType,
    val value: Any?,
    val namespace: String? = null,
    val isArray: Boolean = false,
    val parentId: Int? = null,
    var stringOptions: List<StringOption>? = null,
    var integerOptions: List<IntegerOption>? = null
) {
    fun hasValue(): Boolean {
        return value != ""
    }

    fun getValueLong(): Long {
        return value?.toString()?.toLong() ?: 0
    }

    fun getValueString(): String {
        return value as String
    }

    fun getValueBoolean(): Boolean {
        return value as Boolean
    }

    fun getValueBitmap(): Bitmap {
        return value as Bitmap
    }
}