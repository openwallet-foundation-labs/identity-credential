package com.android.mdl.app.fragment

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.icu.text.SimpleDateFormat
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.Editable
import android.text.InputType
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.View.NOT_FOCUSABLE
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.android.mdl.app.databinding.FragmentSelfSignedDetailsBinding
import com.android.mdl.app.util.Field
import com.android.mdl.app.util.FieldType
import com.android.mdl.app.util.FormatUtil.fullDateStringToMilliseconds
import com.android.mdl.app.util.FormatUtil.millisecondsToFullDateString
import com.android.mdl.app.util.SelfSignedDocumentData
import com.android.mdl.app.util.ProvisionInfo
import com.android.mdl.app.viewmodel.SelfSignedViewModel
import com.google.android.material.datepicker.MaterialDatePicker
import org.jetbrains.anko.collections.forEachWithIndex
import org.jetbrains.anko.horizontalMargin
import org.jetbrains.anko.imageBitmap
import java.io.File
import java.io.IOException
import java.util.*

class SelfSignedDetailsFragment : Fragment() {
    companion object {
        private const val LOG_TAG = "SelfSignedDetailsFragment"
        private const val REQUEST_IMAGE_CAPTURE = 1
    }

    private lateinit var vm: SelfSignedViewModel

    private val args: SelfSignedDetailsFragmentArgs by navArgs()
    private var _binding: FragmentSelfSignedDetailsBinding? = null

    private val binding get() = _binding!!
    private lateinit var provisionInfo: ProvisionInfo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        provisionInfo = args.provisionInfo
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        vm = ViewModelProvider(this)[SelfSignedViewModel::class.java]
        _binding = FragmentSelfSignedDetailsBinding.inflate(inflater)
        binding.fragment = this

        bindUI()

        return binding.root
    }

    private fun bindUI() {
        // Create all fields in the screen
        vm.getFields(provisionInfo.docType).forEach { field ->
            addField(field)
        }

        vm.loading.observe(viewLifecycleOwner) {
            binding.loadingProgress.visibility = it
        }

        vm.created.observe(viewLifecycleOwner) {
            Toast.makeText(
                requireContext(), "Document created successfully!",
                Toast.LENGTH_SHORT
            ).show()
            findNavController().navigate(
                SelfSignedDetailsFragmentDirections.actionSelfSignedDetailsToSelectDocumentFragment()
            )
        }

    }

    override fun onDestroyView() {
        super.onDestroyView()
        updateList()
    }

    private fun updateList() {
        vm.getFields(provisionInfo.docType).forEachWithIndex { i, field ->
            vm.getFields(provisionInfo.docType)[i] = getField(field)
        }
    }

    private fun getField(field: Field): Field {
        return when (field.fieldType) {
            FieldType.BITMAP -> {
                Field(
                    field.id,
                    field.label,
                    field.name,
                    field.fieldType,
                    getImageViewValue(field.id)
                )
            }
            FieldType.BOOLEAN -> {
                // TODO: get value from switch button for boolean type
                Field(field.id, field.label, field.name, field.fieldType, getViewValue(field.id))
            }
            FieldType.STRING, FieldType.DATE -> {
                Field(field.id, field.label, field.name, field.fieldType, getViewValue(field.id))
            }
        }
    }

    private fun addField(field: Field) {
        when (field.fieldType) {
            FieldType.BITMAP -> {
                binding.layoutSelfSignedDetails.addView(
                    getTextView(field.id + 500, field.label, null)
                )
                binding.layoutSelfSignedDetails.addView(
                    getImageView(field.id, field.value as Bitmap) { dispatchTakePictureIntent(field.id) }
                )
            }
            FieldType.BOOLEAN -> {
                // TODO: create switch button for boolean type
                binding.layoutSelfSignedDetails.addView(
                    getTextView(field.id + 500, field.label, null)
                )
                binding.layoutSelfSignedDetails.addView(
                    getEditView(field.id, field.value as String, null)
                )
            }
            FieldType.STRING -> {
                binding.layoutSelfSignedDetails.addView(
                    getTextView(field.id + 500, field.label, null)
                )
                binding.layoutSelfSignedDetails.addView(
                    getEditView(field.id, field.value as String, null)
                )
            }
            FieldType.DATE -> {
                binding.layoutSelfSignedDetails.addView(
                    getTextView(field.id + 500, field.label, null)
                )
                binding.layoutSelfSignedDetails.addView(
                    getEditView(field.id, field.value as String, picker(field.id, field.id + 500))
                )
            }
        }
    }

    private fun getImageView(
        id: Int,
        bitmap: Bitmap,
        onClickListener: View.OnClickListener?
    ): View {
        val imageView = ImageView(requireContext())
        imageView.id = id
        imageView.imageBitmap = bitmap

        imageView.layoutParams = LinearLayout.LayoutParams(bitmap.width, bitmap.height).also {
            it.horizontalMargin = 16
            it.topMargin = 16
        }
        onClickListener?.let {
            imageView.setOnClickListener(it)
        }
        return imageView
    }

    private fun getTextView(id: Int, value: String, onClickListener: View.OnClickListener?): View {
        val textView = TextView(requireContext())
        textView.id = id
        textView.text = value
        textView.layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).also {
            it.horizontalMargin = 16
            it.topMargin = 16
        }
        onClickListener?.let {
            textView.setOnClickListener(it)
        }
        return textView
    }

    private fun getEditView(id: Int, value: String, onClickListener: View.OnClickListener?): View {
        val editText = EditText(requireContext())
        editText.id = id
        editText.text = Editable.Factory.getInstance().newEditable(value)
        editText.layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).also {
            it.horizontalMargin = 16
        }
        onClickListener?.let {
            editText.setOnClickListener(it)
            // avoid open keyboard
            editText.inputType = InputType.TYPE_NULL
            editText.focusable = NOT_FOCUSABLE
        }
        return editText
    }

    fun onCreateSelfSigned() {
        updateList()
        val dData = SelfSignedDocumentData(
            provisionInfo,
            vm.getFields(provisionInfo.docType)
        )
        vm.createSelfSigned(dData)
        binding.loadingProgress.visibility = View.VISIBLE
    }

    private fun getImageViewValue(id: Int): Bitmap {
        val imageView = binding.layoutSelfSignedDetails.findViewById<ImageView>(id)
        val bitmap = Bitmap.createBitmap(
            imageView.width, imageView.height, Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        imageView.draw(canvas)
        return bitmap
    }

    private fun getViewValue(id: Int): String {
        return when (val view = binding.layoutSelfSignedDetails.findViewById<View>(id)) {
            is TextView -> {
                view.text.toString()
            }
            else -> {
                String()
            }
        }
    }

    private fun setViewValue(id: Int, value: String) {
        val view = binding.layoutSelfSignedDetails.findViewById<View>(id)
        if (view is TextView) {
            view.text = value
        }
    }

    /**
     * OnClickListener for date picker
     */
    private fun picker(id: Int, idLabel: Int) = View.OnClickListener {
        val titleText = getViewValue(idLabel)
        val dateText = getViewValue(id)
        Log.d(LOG_TAG, "$dateText - ${fullDateStringToMilliseconds(dateText)}")
        val datePicker =
            MaterialDatePicker.Builder.datePicker()
                .setTitleText(titleText)
                .setSelection(fullDateStringToMilliseconds(dateText))
                .build()
        datePicker.addOnPositiveButtonClickListener {
            Log.d(LOG_TAG, "$it - ${millisecondsToFullDateString(it)}")
            setViewValue(id, millisecondsToFullDateString(it))
        }
        datePicker.show(parentFragmentManager, view?.tag?.toString())
    }

    // Following to enable take picture
    lateinit var currentPhotoPath: String
    private var imageViewId: Int? = null

    @Throws(IOException::class)
    private fun createImageFile(): File {
        // Create an image file name
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val storageDir: File? = requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "JPEG_${timeStamp}_", /* prefix */
            ".jpg", /* suffix */
            storageDir /* directory */
        ).apply {
            // Save a file: path for use with ACTION_VIEW intents
            currentPhotoPath = absolutePath
        }
    }

    private fun dispatchTakePictureIntent(viewId: Int) {
        imageViewId = viewId

        // Check if there is a camera.
        val packageManager = requireContext().packageManager
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
            Toast.makeText(activity, "This device does not have a camera.", Toast.LENGTH_SHORT)
                .show()
            return
        }

        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
            // Ensure that there's a camera activity to handle the intent
            if (takePictureIntent.resolveActivity(packageManager) == null) {
                Toast.makeText(activity, "Could not find camera activity.", Toast.LENGTH_SHORT)
                    .show()
                return
            }
            takePictureIntent.resolveActivity(packageManager)?.also {
                // Create the File where the photo should go
                val photoFile: File? = try {
                    createImageFile()
                } catch (ex: IOException) {
                    Log.e(LOG_TAG, "Error capturing image", ex)
                    null
                }
                // Continue only if the File was successfully created
                photoFile?.also {
                    val photoURI: Uri = FileProvider.getUriForFile(
                        requireContext(),
                        "com.android.mdl.app.fileprovider",
                        it
                    )
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                    Log.d(LOG_TAG, "Start activity $currentPhotoPath")
                    startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE)
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            Log.d(LOG_TAG, "Picture taken $currentPhotoPath")
            setPic()
        }
    }

    private fun setPic() {
        val id = imageViewId
        if (id == null) {
            Log.e(LOG_TAG, "No image view id, impossible to set picture")
            return
        }

        val imageView = binding.layoutSelfSignedDetails.findViewById<ImageView>(id)

        // Get the dimensions of the View
        val targetW: Int = imageView.width
        val targetH: Int = imageView.height

        val bmOptions = BitmapFactory.Options().apply {
            // Get the dimensions of the bitmap
            inJustDecodeBounds = true

            val photoW: Int = outWidth
            val photoH: Int = outHeight

            // Determine how much to scale down the image
            val scaleFactor: Int = Math.max(1, Math.min(photoW / targetW, photoH / targetH))

            // Decode the image file into a Bitmap sized to fill the View
            inJustDecodeBounds = false
            inSampleSize = scaleFactor
            inPurgeable = true
        }
        BitmapFactory.decodeFile(currentPhotoPath, bmOptions)?.also { bitmap ->
            imageView.setImageBitmap(bitmap)
        }
    }
}

