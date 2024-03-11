package com.android.identity_credential.wallet

import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import kotlinx.datetime.Instant

@Immutable
data class Card(
    // The identifier of the card
    val id: String,

    // Display string for the card name.
    val name: String,

    // Display string for the issuer of the card.
    val issuer: String,

    // Display string for the type of the card
    val typeName: String,

    // Display artwork.
    val artwork: Bitmap,

    // Point in time that information for the card was last refreshed from the issuer
    val lastRefresh: Instant,

    // Human-readable string explaining the user what state the card is in.
    val status: String,

    // Data attributes
    val attributes: Map<String, String>,

    // Data attribute: Portrait of the card holder, if available
    val attributePortrait: Bitmap?,

    // Data attribute: Signature or usual mark of the holder, if available
    val attributeSignatureOrUsualMark: Bitmap?,

    val keyInfos: List<CardKeyInfo>
)
