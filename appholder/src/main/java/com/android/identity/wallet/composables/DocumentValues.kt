package com.android.identity.wallet.composables

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Brush
import com.android.identity.wallet.R
import com.android.identity.wallet.document.DocumentColor
import com.android.identity.wallet.document.SecureAreaImplementationState
import com.android.identity.wallet.theme.BlueGradient
import com.android.identity.wallet.theme.GreenGradient
import com.android.identity.wallet.theme.RedGradient
import com.android.identity.wallet.theme.YellowGradient

@StringRes
fun keystoreNameFor(implementation: SecureAreaImplementationState): Int {
    return when (implementation) {
        is SecureAreaImplementationState.Android -> R.string.keystore_android
        is SecureAreaImplementationState.BouncyCastle -> R.string.keystore_bouncy_castle
    }
}

fun Int.toCardArt(): DocumentColor {
    return when (this) {
        1 -> DocumentColor.Yellow
        2 -> DocumentColor.Blue
        3 -> DocumentColor.Red
        else -> DocumentColor.Green
    }
}

fun gradientFor(cardArt: DocumentColor): Brush {
    return when (cardArt) {
        is DocumentColor.Green -> GreenGradient
        is DocumentColor.Yellow -> YellowGradient
        is DocumentColor.Blue -> BlueGradient
        is DocumentColor.Red -> RedGradient
    }
}