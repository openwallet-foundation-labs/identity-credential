package com.android.mdl.appreader.home

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable

@Stable
@Immutable
data class DocumentElementsRequest(
    @StringRes val title: Int,
    val isSelected: Boolean = false,
    var attributes: List<String>? = null
)