package org.multipaz.provisioning.common

import org.multipaz.flow.annotation.FlowState
import org.multipaz.provisioning.IssuingAuthority

@FlowState(
    flowInterface = IssuingAuthority::class
)
abstract class AbstractIssuingAuthorityState