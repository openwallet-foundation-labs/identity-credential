package com.android.mdl.app.documentdata

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.text.HtmlCompat
import androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.navArgs
import com.android.mdl.app.databinding.FragmentShowDocumentDataBinding
import com.android.mdl.app.transfer.TransferManager

class ShowDocumentDataFragment : Fragment() {

    private val arguments by navArgs<ShowDocumentDataFragmentArgs>()
    private var _binding: FragmentShowDocumentDataBinding? = null
    private val binding get() = _binding!!
    private lateinit var transferManager: TransferManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        transferManager = TransferManager.getInstance(requireContext())
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
        loadDocumentElements()
    }

    private fun loadDocumentElements() {
        transferManager.readDocumentEntries(arguments.documentName).let { result ->
            binding.tvResults.text = HtmlCompat.fromHtml(result.text, FROM_HTML_MODE_LEGACY)
            result.portrait?.let { portrait ->
                binding.ivPortrait.setImageBitmap(portrait)
                binding.ivPortrait.visibility = View.VISIBLE
            }
            result.signature?.let { signature ->
                binding.ivSignature.setImageBitmap(signature)
                binding.ivSignature.visibility = View.VISIBLE
            }
        }
    }
}