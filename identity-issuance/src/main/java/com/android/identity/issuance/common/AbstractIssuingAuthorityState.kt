package com.android.identity.issuance.common

import com.android.identity.flow.annotation.FlowState
import com.android.identity.issuance.IssuingAuthority

@FlowState(
    flowInterface = IssuingAuthority::class
)
abstract class AbstractIssuingAuthorityState