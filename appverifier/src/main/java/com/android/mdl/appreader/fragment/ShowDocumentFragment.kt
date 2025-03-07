package com.android.mdl.appreader.fragment

import android.content.res.Resources
import android.icu.text.SimpleDateFormat
import android.icu.util.GregorianCalendar
import android.icu.util.TimeZone
import android.os.Bundle
import android.text.Html
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.annotation.AttrRes
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import org.multipaz.cbor.Cbor
import org.multipaz.crypto.X509Cert
import org.multipaz.documenttype.DocumentAttributeType
import org.multipaz.documenttype.MdocDataElement
import org.multipaz.crypto.javaPublicKey
import org.multipaz.crypto.javaX509Certificate
import org.multipaz.jpeg2k.Jpeg2kConverter
import org.multipaz.mdoc.response.DeviceResponseParser
import com.android.mdl.appreader.R
import com.android.mdl.appreader.VerifierApp
import com.android.mdl.appreader.databinding.FragmentShowDocumentBinding
import com.android.mdl.appreader.transfer.TransferManager
import com.android.mdl.appreader.trustmanagement.CustomValidators
import com.android.mdl.appreader.trustmanagement.getCommonName
import com.android.mdl.appreader.util.FormatUtil
import com.android.mdl.appreader.util.TransferStatus
import com.android.mdl.appreader.util.logDebug
import java.security.MessageDigest
import java.security.cert.X509Certificate

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class ShowDocumentFragment : Fragment() {

    companion object {
        private const val TAG = "ShowDocumentFragment"
        private const val MDL_DOCTYPE = "org.iso.18013.5.1.mDL"
        private const val MDL_NAMESPACE = "org.iso.18013.5.1"
    }

    private var _binding: FragmentShowDocumentBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!
    private var portraitBytes: ByteArray? = null
    private var signatureBytes: ByteArray? = null
    private lateinit var transferManager: TransferManager

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentShowDocumentBinding.inflate(inflater, container, false)
        transferManager = TransferManager.getInstance(requireContext())
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, callback)

        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val documents = transferManager.getDeviceResponse().documents
        binding.tvResults.text =
            Html.fromHtml(formatTextResult(documents), Html.FROM_HTML_MODE_COMPACT)

        portraitBytes?.let { pb ->
            logDebug("Showing portrait " + pb.size + " bytes")
            binding.ivPortrait.setImageBitmap(
                Jpeg2kConverter.decodeByteArray(requireContext(), portraitBytes!!)
            )
            binding.ivPortrait.visibility = View.VISIBLE
        }

        signatureBytes?.let { signature ->
            logDebug("Showing signature " + signature.size + " bytes")
            binding.ivSignature.setImageBitmap(
                Jpeg2kConverter.decodeByteArray(requireContext(), signatureBytes!!)
            )
            binding.ivSignature.visibility = View.VISIBLE
        }

        binding.btOk.setOnClickListener {
            findNavController().navigate(R.id.action_ShowDocument_to_RequestOptions)
        }
        binding.btCloseConnection.setOnClickListener {
            transferManager.stopVerification(
                sendSessionTerminationMessage = false,
                useTransportSpecificSessionTermination = false
            )
            hideButtons()
        }
        binding.btCloseTransportSpecific.setOnClickListener {
            transferManager.stopVerification(
                sendSessionTerminationMessage = true,
                useTransportSpecificSessionTermination = true
            )
            hideButtons()
        }
        binding.btCloseTerminationMessage.setOnClickListener {
            transferManager.stopVerification(
                sendSessionTerminationMessage = true,
                useTransportSpecificSessionTermination = false
            )
            hideButtons()
        }
        binding.btNewRequest.setOnClickListener {
            findNavController().navigate(
                ShowDocumentFragmentDirections.actionShowDocumentToRequestOptions(true)
            )
        }
        transferManager.getTransferStatus().observe(viewLifecycleOwner) {
            when (it) {
                TransferStatus.ENGAGED -> {
                    logDebug("Device engagement received.")
                }

                TransferStatus.CONNECTED -> {
                    logDebug("Device connected received.")
                }

                TransferStatus.RESPONSE -> {
                    logDebug("Device response received.")
                }

                TransferStatus.DISCONNECTED -> {
                    logDebug("Device disconnected received.")
                    hideButtons()
                }

                TransferStatus.ERROR -> {
                    Toast.makeText(
                        requireContext(), "Error with the connection.",
                        Toast.LENGTH_SHORT
                    ).show()
                    transferManager.disconnect()
                    hideButtons()
                }

                else -> {}
            }
        }
    }

    private fun hideButtons() {
        binding.btOk.visibility = View.VISIBLE
        binding.btCloseConnection.visibility = View.GONE
        binding.btCloseTransportSpecific.visibility = View.GONE
        binding.btCloseTerminationMessage.visibility = View.GONE
        binding.btNewRequest.visibility = View.GONE
    }

    private fun formatTextResult(documents: Collection<DeviceResponseParser.Document>): String {
        val sb = StringBuffer()

        for (doc in documents) {
            if (!checkPortraitPresenceIfRequired(doc)) {
                // Warn if portrait isn't included in the response.
                sb.append(
                    "<h3>WARNING: <font color=\"red\">No portrait image provided "
                            + "for ${doc.docType}.</font></h3><br>"
                )
                sb.append(
                    "<i>This means it's not possible to verify the presenter is the authorized "
                            + "holder. Be careful doing any business transactions or inquiries until "
                            + "proper identification is confirmed.</i><br>"
                )
                sb.append("<br>")
            }
        }

        // Get primary color from theme to use in the HTML formatted document.
        val primaryColor = String.format(
            "#%06X",
            0xFFFFFF and requireContext().theme.attr(androidx.appcompat.R.attr.colorPrimary).data
        )

        val totalDuration = transferManager.getTapToEngagementDurationMillis() +
                transferManager.getEngagementToRequestDurationMillis() +
                transferManager.getRequestToResponseDurationMillis()
        sb.append("Tap to Engagement Received: ${transferManager.getTapToEngagementDurationMillis()} ms<br>")
        val scanningText = if (transferManager.getBleScanningMillis() > 0) {
            "${transferManager.getBleScanningMillis()} ms"
        } else {
            "N/A"
        }
        sb.append("BLE scanning: $scanningText ms<br>")
        sb.append("Engagement Received to Request Sent: ${transferManager.getEngagementToRequestDurationMillis()} ms<br>")
        sb.append("Request Sent to Response Received: ${transferManager.getRequestToResponseDurationMillis()} ms<br>")
        sb.append("<b>Total transaction time: <font color=\"$primaryColor\">$totalDuration ms</font></b><br>")
        sb.append("<br>")

        sb.append("Engagement Method: <b>" + transferManager.getEngagementMethod() + "</b><br>")
        sb.append("Device Retrieval Method: <b>" + transferManager.mdocConnectionMethod + "</b><br>")
        sb.append("Session encryption curve: <b>" + transferManager.getMdocSessionEncryptionCurve() + "</b><br>")
        sb.append("<br>")

        for (doc in documents) {
            sb.append("<h3>Doctype: <font color=\"$primaryColor\">${doc.docType}</font></h3>")
            val cc = doc.issuerCertificateChain.certificates
            var certChain: List<X509Cert> = cc
            val customValidators = CustomValidators.getByDocType(doc.docType)
            val result = VerifierApp.trustManagerInstance.verify(
                chain = certChain,
                //customValidators = customValidators
            )
            if (result.trustChain != null) {
                certChain = result.trustChain!!.certificates
            }
            if (!result.isTrusted) {
                sb.append("${getFormattedCheck(false)}Error in certificate chain validation: ${result.error?.message}<br>")
            }

            val commonName = certChain.last().issuer.name
            sb.append("${getFormattedCheck(result.isTrusted)}Issuer’s DS Key Recognized: ($commonName)<br>")
            sb.append("${getFormattedCheck(doc.issuerSignedAuthenticated)}Issuer Signed Authenticated<br>")
            var macOrSignatureString = "MAC"
            if (doc.deviceSignedAuthenticatedViaSignature)
                macOrSignatureString = "ECDSA"
            sb.append("${getFormattedCheck(doc.deviceSignedAuthenticated)}Device Signed Authenticated (${macOrSignatureString})<br>")

            sb.append("<h6>MSO</h6>")

            val df = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX")
            val calSigned = GregorianCalendar(TimeZone.getTimeZone("UTC"))
            val calValidFrom = GregorianCalendar(TimeZone.getTimeZone("UTC"))
            val calValidUntil = GregorianCalendar(TimeZone.getTimeZone("UTC"))
            calSigned.timeInMillis = doc.validityInfoSigned.toEpochMilliseconds()
            calValidFrom.timeInMillis = doc.validityInfoValidFrom.toEpochMilliseconds()
            calValidUntil.timeInMillis = doc.validityInfoValidUntil.toEpochMilliseconds()
            sb.append("${getFormattedCheck(true)}Signed: ${df.format(calSigned)}<br>")
            sb.append("${getFormattedCheck(true)}Valid From: ${df.format(calValidFrom)}<br>")
            sb.append("${getFormattedCheck(true)}Valid Until: ${df.format(calValidUntil)}<br>")
            if (doc.validityInfoExpectedUpdate != null) {
                val calExpectedUpdate = GregorianCalendar(TimeZone.getTimeZone("UTC"))
                calExpectedUpdate.timeInMillis = doc.validityInfoExpectedUpdate!!.toEpochMilliseconds()
                sb.append("${getFormattedCheck(true)}Expected Update: ${df.format(calExpectedUpdate)}<br>")
            }
            // TODO: show warning if MSO is valid for more than 30 days

            // Just show the SHA-1 of DeviceKey since all we're interested in here is whether
            // we saw the same key earlier.
            sb.append("<h6>DeviceKey</h6>")
            sb.append("${getFormattedCheck(true)}Curve: <b>${doc.deviceKey.curve}</b><br>")
            val deviceKeySha1 = FormatUtil.encodeToString(
                MessageDigest.getInstance("SHA-1").digest(doc.deviceKey.javaPublicKey.encoded)
            )
            sb.append("${getFormattedCheck(true)}SHA-1: ${deviceKeySha1}<br>")
            // TODO: log DeviceKey's that we've seen and show warning if a DeviceKey is seen
            //  a second time. Also would want button in Settings page to clear the log.

            for (ns in doc.issuerNamespaces) {
                sb.append("<br>")
                sb.append("<h5>Namespace: $ns</h5>")
                sb.append("<p>")
                for (elem in doc.getIssuerEntryNames(ns)) {
                    val value: ByteArray = doc.getIssuerEntryData(ns, elem)
                    var valueStr: String
                    val mdocDataElement =
                        VerifierApp.documentTypeRepositoryInstance
                            .getDocumentTypeForMdoc(doc.docType)
                            ?.mdocDocumentType
                            ?.namespaces?.get(ns)?.dataElements?.get(elem)
                    val name = mdocDataElement?.attribute?.displayName ?: elem
                    if (isPortraitElement(mdocDataElement)) {
                        valueStr = String.format("(%d bytes, shown above)", value.size)
                        portraitBytes = doc.getIssuerEntryByteString(ns, elem)
                    } else if (doc.docType == MDL_DOCTYPE && ns == MDL_NAMESPACE && elem == "extra") {
                        valueStr = String.format("%d bytes extra data", value.size)
                    } else if (doc.docType == MDL_DOCTYPE && ns == MDL_NAMESPACE && elem == "signature_usual_mark") {
                        valueStr = String.format("(%d bytes, shown below)", value.size)
                        signatureBytes = doc.getIssuerEntryByteString(ns, elem)
                    } else if (doc.docType == MDL_DOCTYPE && ns == MDL_NAMESPACE && elem == "driving_privileges") {
                        valueStr = createDrivingPrivilegesHtml(value)
                    } else if (mdocDataElement != null) {
                        valueStr = mdocDataElement.renderValue(Cbor.decode(value))
                    } else {
                        valueStr = Cbor.toDiagnostics(value)
                    }
                    sb.append(
                        "${
                            getFormattedCheck(
                                doc.getIssuerEntryDigestMatch(
                                    ns,
                                    elem
                                )
                            )
                        }<b>$name</b> -> $valueStr<br>"
                    )
                }
                sb.append("</p><br>")
            }
        }
        return sb.toString()
    }

    private fun createDrivingPrivilegesHtml(encodedElementValue: ByteArray): String {
        val decodedValue = Cbor.decode(encodedElementValue).asArray
        val htmlDisplayValue = buildString {
            for (categoryMap in decodedValue) {
                val categoryCode =
                    categoryMap.getOrNull("vehicle_category_code")?.asTstr ?: "Unspecified"
                val vehicleIndent = "&nbsp;".repeat(4)
                append("<div>${vehicleIndent}Vehicle class: $categoryCode</div>")
                val indent = "&nbsp;".repeat(8)
                categoryMap.getOrNull("issue_date")?.asDateString?.let { append("<div>${indent}Issued: $it</div>") }
                categoryMap.getOrNull("expiry_date")?.asDateString?.let { append("<div>${indent}Expires: $it</div>") }
            }
        }
        return htmlDisplayValue
    }

    private fun isPortraitApplicable(docType: String, namespace: String?): Boolean {
        val hasPortrait = docType == MDL_DOCTYPE
        val namespaceContainsPortrait = namespace == MDL_NAMESPACE
        return hasPortrait && namespaceContainsPortrait
    }

    private fun isPortraitElement(mdocDataElement: MdocDataElement?): Boolean {
        if (mdocDataElement?.attribute?.type != DocumentAttributeType.Picture) {
            return false
        }
        return listOf("portrait", "fac").contains(mdocDataElement.attribute.identifier)
    }

    // ISO/IEC 18013-5 requires the portrait image to be shared if the portrait was requested and if any other data element is released
    private fun checkPortraitPresenceIfRequired(document: DeviceResponseParser.Document): Boolean {
        document.issuerNamespaces.forEach { ns ->
            val portraitApplicable = isPortraitApplicable(document.docType, ns)
            if (portraitApplicable) {
                val entries = document.getIssuerEntryNames(ns)
                val isPortraitMandatory = entries.isNotEmpty()
                val isPortraitMissing = !entries.contains("portrait")
                // check if other data elements are released but portrait is not present
                if (isPortraitMandatory && isPortraitMissing) {
                    return false
                }
            }
        }
        return true
    }

    private fun Resources.Theme.attr(@AttrRes attribute: Int): TypedValue {
        val typedValue = TypedValue()
        if (!resolveAttribute(attribute, typedValue, true)) {
            throw IllegalArgumentException("Failed to resolve attribute: $attribute")
        }
        return typedValue
    }

    private fun getFormattedCheck(authenticated: Boolean) = if (authenticated) {
        "<font color=green>&#x2714;</font> "
    } else {
        "<font color=red>&#x274C;</font> "
    }

    private var callback = object : OnBackPressedCallback(true /* enabled by default */) {
        override fun handleOnBackPressed() {
            TransferManager.getInstance(requireContext()).disconnect()
            findNavController().navigate(R.id.action_ShowDocument_to_RequestOptions)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}