package com.android.mdl.appreader.fragment

import android.content.res.Resources
import android.graphics.BitmapFactory
import android.icu.text.SimpleDateFormat
import android.icu.util.GregorianCalendar
import android.icu.util.TimeZone
import android.os.Bundle
import android.text.Html
import android.util.Base64
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.annotation.AttrRes
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.android.identity.android.mdoc.util.CredmanUtil
import org.multipaz.cbor.Cbor
import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.Crypto
import org.multipaz.crypto.EcCurve
import org.multipaz.crypto.EcPublicKeyDoubleCoordinate
import org.multipaz.crypto.X509CertChain
import org.multipaz.crypto.javaPublicKey
import org.multipaz.crypto.javaX509Certificates
import org.multipaz.crypto.toEcPrivateKey
import org.multipaz.mdoc.response.DeviceResponseParser
import com.android.mdl.appreader.R
import com.android.mdl.appreader.VerifierApp
import com.android.mdl.appreader.databinding.FragmentShowDeviceResponseBinding
import com.android.mdl.appreader.transfer.TransferManager
import com.android.mdl.appreader.trustmanagement.CustomValidators
import com.android.mdl.appreader.trustmanagement.getCommonName
import com.android.mdl.appreader.util.FormatUtil
import com.android.mdl.appreader.util.logDebug
import org.json.JSONObject
import java.security.MessageDigest
import java.security.interfaces.ECPublicKey

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class ShowDeviceResponseFragment : Fragment() {

    companion object {
        private const val MDL_DOCTYPE = "org.iso.18013.5.1.mDL"
        private const val MICOV_DOCTYPE = "org.micov.1"
        private const val MDL_NAMESPACE = "org.iso.18013.5.1"
        private const val MICOV_ATT_NAMESPACE = "org.micov.attestation.1"
        private const val EU_PID_DOCTYPE = "eu.europa.ec.eudi.pid.1"
        private const val EU_PID_NAMESPACE = "eu.europa.ec.eudi.pid.1"
    }

    private val args: ShowDeviceResponseFragmentArgs by navArgs()

    private var _binding: FragmentShowDeviceResponseBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!
    private var portraitBytes: ByteArray? = null
    private var signatureBytes: ByteArray? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentShowDeviceResponseBinding.inflate(inflater, container, false)
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, callback)

        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val responseJson = JSONObject(args.bundle.getString("responseJson")!!)
        val nonce = args.bundle.getByteArray("nonce")!!
        val requestIdentityKeyPair = args.requestIdentityKeyPair
        val requestIdentityKey = requestIdentityKeyPair.private.toEcPrivateKey(
            requestIdentityKeyPair.public,
            EcCurve.P256
        )

        val encryptedCredentialDocumentBase64 = responseJson.getString("token")!!
        val encryptedCredentialDocument = Base64.decode(encryptedCredentialDocumentBase64, Base64.URL_SAFE or Base64.NO_WRAP )

        val (cipherText, encapsulatedPublicKey) = CredmanUtil.parseCredentialDocument(encryptedCredentialDocument)

        val uncompressed = (requestIdentityKey.publicKey as EcPublicKeyDoubleCoordinate).asUncompressedPointEncoding
        val encodedSessionTranscript = CredmanUtil.generateAndroidSessionTranscript(
            nonce,
            requireContext().packageName,
            Crypto.digest(Algorithm.SHA256, uncompressed)
        )

        val encodedDeviceResponse = Crypto.hpkeDecrypt(
            Algorithm.HPKE_BASE_P256_SHA256_AES128GCM,
            requestIdentityKey,
            cipherText,
            encodedSessionTranscript,
            encapsulatedPublicKey)

        val parser = DeviceResponseParser(encodedDeviceResponse, encodedSessionTranscript)
        val deviceResponse = parser.parse()

        val documents = deviceResponse.documents
        binding.tvResults.text =
            Html.fromHtml(formatTextResult(documents), Html.FROM_HTML_MODE_COMPACT)

        portraitBytes?.let { pb ->
            logDebug("Showing portrait " + pb.size + " bytes")
            binding.ivPortrait.setImageBitmap(
                BitmapFactory.decodeByteArray(portraitBytes, 0, pb.size)
            )
            binding.ivPortrait.visibility = View.VISIBLE
        }

        signatureBytes?.let { signature ->
            logDebug("Showing signature " + signature.size + " bytes")
            binding.ivSignature.setImageBitmap(
                BitmapFactory.decodeByteArray(signatureBytes, 0, signature.size)
            )
            binding.ivSignature.visibility = View.VISIBLE
        }

        binding.btClose.setOnClickListener {
            findNavController().navigate(
                ShowDeviceResponseFragmentDirections.toRequestOptions(false)
            )
        }


    }

    private fun formatTextResult(documents: Collection<DeviceResponseParser.Document>): String {
        val sb = StringBuffer()

        for (doc in documents) {
            if (!checkPortraitPresenceIfRequired(doc)) {
                // Warn if portrait isn't included in the response.
                sb.append("<h3>WARNING: <font color=\"red\">No portrait image provided "
                        + "for ${doc.docType}.</font></h3><br>")
                sb.append("<i>This means it's not possible to verify the presenter is the authorized "
                        + "holder. Be careful doing any business transactions or inquiries until "
                        + "proper identification is confirmed.</i><br>")
                sb.append("<br>")
            }
        }

        sb.append("Number of documents returned: <b>${documents.size}</b><br>")
        //sb.append("Address: <b>" + transferManager.mdocConnectionMethod + "</b><br>")
        sb.append("<br>")
        for (doc in documents) {
            // Get primary color from theme to use in the HTML formatted document.
            val color = String.format(
                "#%06X",
                0xFFFFFF and requireContext().theme.attr(androidx.appcompat.R.attr.colorPrimary).data
            )
            sb.append("<h3>Doctype: <font color=\"$color\">${doc.docType}</font></h3>")

            var certChain = doc.issuerCertificateChain
            val customValidators = CustomValidators.getByDocType(doc.docType)
            val result = VerifierApp.trustManagerInstance.verify(
                chain = certChain.certificates,
                //customValidators = customValidators
            )
            if (result.trustChain != null) {
                certChain = result.trustChain!!
            }
            if (!result.isTrusted) {
                sb.append("${getFormattedCheck(false)}Error in certificate chain validation: ${result.error?.message}<br>")
            }
            val commonName = certChain.certificates.last().issuer.name
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
                    println("mdocDataElement: $mdocDataElement")
                    if (isPortraitElement(doc.docType, ns, elem)) {
                        valueStr = String.format("(%d bytes, shown above)", value.size)
                        portraitBytes = doc.getIssuerEntryByteString(ns, elem)
                    } else if (doc.docType == MICOV_DOCTYPE && ns == MICOV_ATT_NAMESPACE && elem == "fac") {
                        valueStr = String.format("(%d bytes, shown above)", value.size)
                        portraitBytes = doc.getIssuerEntryByteString(ns, elem)
                    } else if (doc.docType == MDL_DOCTYPE && ns == MDL_NAMESPACE && elem == "extra") {
                        valueStr = String.format("%d bytes extra data", value.size)
                    } else if (doc.docType == MDL_DOCTYPE && ns == MDL_NAMESPACE && elem == "signature_usual_mark") {
                        valueStr = String.format("(%d bytes, shown below)", value.size)
                        signatureBytes = doc.getIssuerEntryByteString(ns, elem)
                    } else if (mdocDataElement != null) {
                        valueStr = mdocDataElement.renderValue(Cbor.decode(value))
                    } else {
                        valueStr = Cbor.toDiagnostics(value)
                    }
                    val elemName = mdocDataElement?.attribute?.displayName ?: elem
                    val checkMark = getFormattedCheck(doc.getIssuerEntryDigestMatch(ns, elem))
                    sb.append("$checkMark<b>$elemName</b> -> $valueStr<br>")
                }
                sb.append("</p><br>")
            }
        }
        return sb.toString()
    }

    private fun isPortraitApplicable(docType: String, namespace: String?): Boolean{
        val hasPortrait = docType == MDL_DOCTYPE
        val namespaceContainsPortrait = namespace == MDL_NAMESPACE
        return hasPortrait && namespaceContainsPortrait
    }

    private fun isPortraitElement(
        docType: String,
        namespace: String?,
        entryName: String?
    ): Boolean {
        val portraitApplicable = isPortraitApplicable(docType, namespace)
        return portraitApplicable && entryName == "portrait"
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
            findNavController().navigate(R.id.toRequestOptions)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}