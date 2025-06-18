package org.multipaz.models.verification

import kotlinx.io.bytestring.ByteString
import org.multipaz.mdoc.connectionmethod.MdocConnectionMethod
import org.multipaz.mdoc.engagement.EngagementParser
import org.multipaz.mdoc.transport.MdocTransport
import org.multipaz.mdoc.transport.MdocTransportOptions

data class MdocVerificationMechanism(
    val connectionMethod: MdocConnectionMethod,
    val options: MdocTransportOptions,
    val encodedDeviceEngagement: ByteString,
    val existingTransport: MdocTransport? = null,
): VerificationMechanism()