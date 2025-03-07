package org.multipaz.issuance.common

import org.multipaz.flow.annotation.FlowState
import org.multipaz.issuance.IssuingAuthority

@FlowState(
    flowInterface = IssuingAuthority::class
)
abstract class AbstractIssuingAuthorityState