package com.android.mdl.appreader.fragment

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.android.mdl.appreader.R
import com.android.mdl.appreader.databinding.FragmentShowQrBinding
import com.android.mdl.appreader.transfer.TransferManager
import com.android.mdl.appreader.util.TransferStatus
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix

class ShowQrFragment : Fragment() {

    companion object {
        private const val LOG_TAG = "ShowQrFragment"
    }

    private val args: ShowQrFragmentArgs by navArgs()

    private var _binding: FragmentShowQrBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!
    private lateinit var transferManager: TransferManager

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentShowQrBinding.inflate(inflater, container, false)
        transferManager = TransferManager.getInstance(requireContext())
        transferManager.initVerificationHelperReverseEngagement()
        return binding.root
    }

    private fun encodeQRCodeAsBitmap(str: String): Bitmap {
        val width = 800
        val result: BitMatrix = try {
            MultiFormatWriter().encode(
                str,
                BarcodeFormat.QR_CODE, width, width, null
            )
        } catch (e: WriterException) {
            throw java.lang.IllegalArgumentException(e)
        }
        val w = result.width
        val h = result.height
        val pixels = IntArray(w * h)
        for (y in 0 until h) {
            val offset = y * w
            for (x in 0 until w) {
                pixels[offset + x] = if (result[x, y]) Color.BLACK else Color.WHITE
            }
        }
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, width, 0, 0, w, h)
        return bitmap
    }

    private fun getViewForReaderEngagementQrCode(readerEngagement : ByteArray): View {
        val base64Encoded = Base64.encodeToString(readerEngagement,
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        val uriEncoded = Uri.Builder()
            .scheme("mdoc://")
            .encodedOpaquePart(base64Encoded)
            .build()
            .toString()
        val qrCodeBitmap = encodeQRCodeAsBitmap(uriEncoded)
        val qrCodeView = ImageView(context)
        qrCodeView.setImageBitmap(qrCodeBitmap)
        return qrCodeView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btCancel.setOnClickListener {
            findNavController().navigate(R.id.action_ShowQr_to_RequestOptions)
        }

        transferManager.getTransferStatus().observe(viewLifecycleOwner) {
            when (it) {
                TransferStatus.READER_ENGAGEMENT_READY -> {
                    Log.d(LOG_TAG, "Reader engagement ready")
                    binding.layoutEngagement.addView(
                        getViewForReaderEngagementQrCode(transferManager.readerEngagement!!)
                    )
                }

                TransferStatus.CONNECTED -> {
                    Log.d(LOG_TAG, "Connected")
                    findNavController().navigate(
                        ShowQrFragmentDirections.actionShowQrToTransfer(
                            args.requestDocumentList
                        )
                    )
                }
                else -> {}
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}