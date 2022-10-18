package com.android.mdl.app.documentdata

import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.text.HtmlCompat
import androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.navArgs
import co.nstant.`in`.cbor.CborDecoder
import co.nstant.`in`.cbor.CborException
import co.nstant.`in`.cbor.model.AbstractFloat
import co.nstant.`in`.cbor.model.Array
import co.nstant.`in`.cbor.model.ByteString
import co.nstant.`in`.cbor.model.DataItem
import co.nstant.`in`.cbor.model.DoublePrecisionFloat
import co.nstant.`in`.cbor.model.MajorType
import co.nstant.`in`.cbor.model.Map
import co.nstant.`in`.cbor.model.NegativeInteger
import co.nstant.`in`.cbor.model.SimpleValue
import co.nstant.`in`.cbor.model.SimpleValueType
import co.nstant.`in`.cbor.model.UnicodeString
import co.nstant.`in`.cbor.model.UnsignedInteger
import com.android.identity.CredentialDataResult
import com.android.identity.CredentialDataResult.Entries.STATUS_USER_AUTHENTICATION_FAILED
import com.android.mdl.app.R
import com.android.mdl.app.databinding.FragmentShowDocumentDataBinding
import com.android.mdl.app.document.Document
import com.android.mdl.app.transfer.TransferManager
import java.io.ByteArrayInputStream
import java.math.BigInteger
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import java.util.concurrent.Executor

class ShowDocumentDataFragment : Fragment() {

    companion object {
        private const val LOG_TAG = "TEST"
        private const val MDL_DOCTYPE = "org.iso.18013.5.1.mDL"
        private const val MICOV_DOCTYPE = "org.micov.1"
        private const val MDL_NAMESPACE = "org.iso.18013.5.1"
        private const val MICOV_ATT_NAMESPACE = "org.micov.attestation.1"
    }

    private val arguments by navArgs<ShowDocumentDataFragmentArgs>()
    private var _binding: FragmentShowDocumentDataBinding? = null
    private val binding get() = _binding!!
    private lateinit var transferManager: TransferManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        transferManager = TransferManager.getInstance(requireContext())
        transferManager.initiate()
    }

    override fun onDestroy() {
        super.onDestroy()
        transferManager.destroy()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentShowDocumentDataBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        showData()
    }

    private fun showData() {
        transferManager.readDocumentEntries(arguments.document)?.let { entries ->
            val documentData = DocumentDataReader(entries).read(arguments.document)
            if (documentData.requestUserAuthorization) {
                sendResponseWithConsent(false)
            } else {
                binding.tvResults.text =
                    HtmlCompat.fromHtml(documentData.text, FROM_HTML_MODE_LEGACY)
                documentData.portrait?.let { portrait ->
                    binding.ivPortrait.setImageBitmap(portrait)
                    binding.ivPortrait.visibility = View.VISIBLE
                }
            }
        }
    }

    private val executor = Executor {
        if (Looper.myLooper() == null) {
            Looper.prepare()
        }
        it.run()
    }

    private fun sendResponseWithConsent(forceLskf: Boolean) {
        val promptInfoBuilder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.bio_auth_title))
            .setSubtitle("We need your consent")

        if (forceLskf) {
            // TODO: this works only on Android 11 or later but for now this is fine
            //   as this is just a reference/test app and this path is only hit if
            //   the user actually presses the "Use PIN" button.  Longer term, we should
            //   fall back to using KeyGuard which will work on all Android versions.
            promptInfoBuilder.setAllowedAuthenticators(BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        } else {
            if (BiometricManager.from(requireContext())
                    .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
            ) {
                promptInfoBuilder.setNegativeButtonText(getString(R.string.bio_auth_use_pin))
            } else {
                // No biometrics enrolled, force use of LSKF
                promptInfoBuilder.setDeviceCredentialAllowed(true)
            }
        }

        val biometricPrompt = BiometricPrompt(this, executor, biometricAuthCallback)
        val cryptoObject = TransferManager.getInstance(requireContext()).getCryptoObject()
        val promptInfo = promptInfoBuilder.build()
        if (cryptoObject != null) {
            biometricPrompt.authenticate(promptInfo, cryptoObject)
        } else {
            biometricPrompt.authenticate(promptInfo)
        }
    }

    private val biometricAuthCallback = object : BiometricPrompt.AuthenticationCallback() {

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            super.onAuthenticationError(errorCode, errString)
            // reached max attempts to authenticate the user, or authentication dialog was cancelled
            if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                // Without this delay, the prompt won't reshow
                Handler(Looper.getMainLooper()).postDelayed({
                    sendResponseWithConsent(true)
                }, 100)
            } else {
                Log.d(
                    LOG_TAG,
                    "Attempt to authenticate the user has failed $errorCode - $errString"
                )
                authenticationFailed()
            }
        }

        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            super.onAuthenticationSucceeded(result)
            Log.d(LOG_TAG, "User authentication succeeded")
            authenticationSucceeded()
        }

        override fun onAuthenticationFailed() {
            super.onAuthenticationFailed()
            Log.d(LOG_TAG, "Attempt to authenticate the user has failed")
        }
    }

    private fun authenticationSucceeded() {
        showData()
    }

    private fun authenticationFailed() {

    }

    class DocumentDataReader(private val entries: CredentialDataResult.Entries) {

        fun read(document: Document): DocumentElements {
            val missingAuth = entries.namespaces.any { namespace ->
                entries.getEntryNames(namespace).any { entry ->
                    entries.getStatus(namespace, entry) == STATUS_USER_AUTHENTICATION_FAILED
                }
            }
            if (missingAuth) {
                return DocumentElements(requestUserAuthorization = true)
            }

            val builder = StringBuilder()
            var portraitBytes: ByteArray? = null
            val docType = document.docType
            entries.namespaces.forEach { ns ->
                builder.append("<br>")
                builder.append("<h5>Namespace: $ns</h5>")
                builder.append("<p>")
                entries.getEntryNames(ns).forEach { entryName ->
                    val byteArray: ByteArray? = entries.getEntry(ns, entryName)
                    byteArray?.let { value ->
                        val valueStr: String
                        if (docType == MDL_DOCTYPE && ns == MDL_NAMESPACE && entryName == "portrait") {
                            valueStr = String.format("(%d bytes, shown above)", value.size)
                            portraitBytes = entries.getEntryBytestring(ns, entryName)
                        } else if (docType == MICOV_DOCTYPE && ns == MICOV_ATT_NAMESPACE && entryName == "fac") {
                            valueStr = String.format("(%d bytes, shown above)", value.size)
                            portraitBytes = entries.getEntryBytestring(ns, entryName)
                        } else if (docType == MDL_DOCTYPE && ns == MDL_NAMESPACE && entryName == "extra") {
                            valueStr = String.format("%d bytes extra data", value.size)
                        } else {
                            valueStr = cborPrettyPrint(value)
                        }
                        builder.append("<b>$entryName</b> -> $valueStr<br>")
                    }
                }
                builder.append("</p><br>")
            }
            val bitmap = portraitBytes?.let { bytes ->
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
            return DocumentElements(builder.toString(), portrait = bitmap)
        }

        private fun cborPrettyPrint(encodedBytes: ByteArray): String {
            val newLine = "<br>"
            val sb = java.lang.StringBuilder()
            val bais = ByteArrayInputStream(encodedBytes)
            val dataItems = try {
                CborDecoder(bais).decode()
            } catch (e: CborException) {
                throw java.lang.IllegalStateException(e)
            }
            for ((count, dataItem) in dataItems.withIndex()) {
                if (count > 0) {
                    sb.append(",$newLine")
                }
                cborPrettyPrintDataItem(sb, 0, dataItem)
            }
            return sb.toString()
        }

        private fun cborPrettyPrintDataItem(
            sb: java.lang.StringBuilder, indent: Int,
            dataItem: DataItem
        ) {
            val space = "&nbsp;"
            val newLine = "<br>"
            val indentBuilder = java.lang.StringBuilder()
            for (n in 0 until indent) {
                indentBuilder.append(space)
            }
            val indentString = indentBuilder.toString()
            if (dataItem.hasTag()) {
                sb.append(String.format("tag %d ", dataItem.tag.value))
            }
            when (dataItem.majorType) {
                MajorType.INVALID ->                 // TODO: throw
                    sb.append("**invalid**")

                MajorType.UNSIGNED_INTEGER -> {
                    // Major type 0: an unsigned integer.
                    val value: BigInteger = (dataItem as UnsignedInteger).value
                    sb.append(value)
                }

                MajorType.NEGATIVE_INTEGER -> {
                    // Major type 1: a negative integer.
                    val value: BigInteger = (dataItem as NegativeInteger).value
                    sb.append(value)
                }

                MajorType.BYTE_STRING -> {
                    // Major type 2: a byte string.
                    val value = (dataItem as ByteString).bytes
                    sb.append("[")
                    for ((count, b) in value.withIndex()) {
                        if (count > 0) {
                            sb.append(", ")
                        }
                        sb.append(String.format("0x%02x", b))
                    }
                    sb.append("]")
                }

                MajorType.UNICODE_STRING -> {
                    // Major type 3: string of Unicode characters that is encoded as UTF-8 [RFC3629].
                    val value = (dataItem as UnicodeString).string
                    // TODO: escape ' in |value|
                    sb.append("'$value'")
                }

                MajorType.ARRAY -> {

                    // Major type 4: an array of data items.
                    val items = (dataItem as Array).dataItems
                    if (items.size == 0) {
                        sb.append("[]")
                    } else if (cborAreAllDataItemsNonCompound(items)) {
                        // The case where everything fits on one line.
                        sb.append("[")
                        for ((count, item) in items.withIndex()) {
                            cborPrettyPrintDataItem(sb, indent, item)
                            if (count + 1 < items.size) {
                                sb.append(", ")
                            }
                        }
                        sb.append("]")
                    } else {
                        sb.append("[$newLine$indentString")
                        for ((count, item) in items.withIndex()) {
                            sb.append("$space$space")
                            cborPrettyPrintDataItem(sb, indent + 2, item)
                            if (count + 1 < items.size) {
                                sb.append(",")
                            }
                            sb.append("$newLine $indentString")
                        }
                        sb.append("]")
                    }
                }

                MajorType.MAP -> {
                    // Major type 5: a map of pairs of data items.
                    val keys = (dataItem as Map).keys
                    if (keys.isEmpty()) {
                        sb.append("{}")
                    } else {
                        sb.append("{$newLine$indentString")
                        for ((count, key) in keys.withIndex()) {
                            sb.append("$space$space")
                            val value = dataItem[key]
                            cborPrettyPrintDataItem(sb, indent + 2, key)
                            sb.append(" : ")
                            cborPrettyPrintDataItem(sb, indent + 2, value)
                            if (count + 1 < keys.size) {
                                sb.append(",")
                            }
                            sb.append("$newLine $indentString")
                        }
                        sb.append("}")
                    }
                }

                MajorType.TAG -> throw java.lang.IllegalStateException("Semantic tag data item not expected")
                MajorType.SPECIAL ->                 // Major type 7: floating point numbers and simple data types that need no
                    // content, as well as the "break" stop code.
                    if (dataItem is SimpleValue) {
                        when (dataItem.simpleValueType) {
                            SimpleValueType.FALSE -> sb.append("false")
                            SimpleValueType.TRUE -> sb.append("true")
                            SimpleValueType.NULL -> sb.append("null")
                            SimpleValueType.UNDEFINED -> sb.append("undefined")
                            SimpleValueType.RESERVED -> sb.append("reserved")
                            SimpleValueType.UNALLOCATED -> sb.append("unallocated")
                        }
                    } else if (dataItem is DoublePrecisionFloat) {
                        val df = DecimalFormat(
                            "0",
                            DecimalFormatSymbols.getInstance(Locale.ENGLISH)
                        )
                        df.maximumFractionDigits = 340
                        sb.append(df.format(dataItem.value))
                    } else if (dataItem is AbstractFloat) {
                        val df = DecimalFormat(
                            "0",
                            DecimalFormatSymbols.getInstance(Locale.ENGLISH)
                        )
                        df.maximumFractionDigits = 340
                        sb.append(df.format(dataItem.value))
                    } else {
                        sb.append("break")
                    }
            }
        }

        // Returns true iff all elements in |items| are not compound (e.g. an array or a map).
        private fun cborAreAllDataItemsNonCompound(items: List<DataItem>): Boolean {
            for (item in items) {
                when (item.majorType) {
                    MajorType.ARRAY, MajorType.MAP -> return false
                    else -> {
                    }
                }
            }
            return true
        }
    }
}