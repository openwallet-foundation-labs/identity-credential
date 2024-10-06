package com.android.identity.appsupport.ui.consent

import com.android.identity.trustmanagement.TrustPoint

/**
 * Details about the Relying Party requesting data.
 *
 * TODO: also add appId.
 *
 * @property trustPoint if the verifier is in a trust-list, the [TrustPoint] indicating this
 * @property websiteOrigin set if the verifier is a website, for example https://gov.example.com
 */
data class ConsentRelyingParty(
    val trustPoint: TrustPoint?,
    val websiteOrigin: String? = null,
)