package com.android.identity.wallet.fragment

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Typeface
import android.icu.text.SimpleDateFormat
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.Editable
import android.text.InputType
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.View.NOT_FOCUSABLE
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.activity.result.contract.ActivityResultContracts.TakePicture
import androidx.core.content.FileProvider
import androidx.core.widget.addTextChangedListener
import androidx.exifinterface.media.ExifInterface
import androidx.exifinterface.media.ExifInterface.ORIENTATION_UNDEFINED
import androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.android.identity.documenttype.DocumentAttributeType
import com.android.identity.documenttype.IntegerOption
import com.android.identity.documenttype.StringOption
import com.android.identity.wallet.databinding.FragmentSelfSignedDetailsBinding
import com.android.identity.wallet.selfsigned.ProvisionInfo
import com.android.identity.wallet.selfsigned.SelfSignedDocumentData
import com.android.identity.wallet.util.Field
import com.android.identity.wallet.util.FormatUtil.fullDateStringToMilliseconds
import com.android.identity.wallet.util.FormatUtil.millisecondsToFullDateString
import com.android.identity.wallet.util.log
import com.android.identity.wallet.util.logError
import com.android.identity.wallet.viewmodel.SelfSignedViewModel
import com.google.android.material.datepicker.MaterialDatePicker
import java.io.File
import java.io.IOException
import java.util.*
import kotlin.math.max
import kotlin.math.min

class SelfSignedDetailsFragment : Fragment() {
    private val vm: SelfSignedViewModel by viewModels()
    private val args: SelfSignedDetailsFragmentArgs by navArgs()
    private val nameElements = listOf("given_name", "name", "gn")

    private var _binding: FragmentSelfSignedDetailsBinding? = null
    private val binding get() = _binding!!

    private lateinit var provisionInfo: ProvisionInfo
    private lateinit var documentNameEditText: EditText
    private lateinit var holderNameEditText: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        provisionInfo = args.provisionInfo
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentSelfSignedDetailsBinding.inflate(inflater)
        binding.fragment = this
        bindUI()
        return binding.root
    }

    private fun bindUI() {
        // Create all fields in the screen
        val documentName = getDocumentNameValue()
        addField(
            Field(
                0,
                "Document Name",
                provisionInfo.docName,
                DocumentAttributeType.String,
                documentName,
            ),
        )
        documentNameEditText = binding.layoutSelfSignedDetails.findViewById(0)
        vm.getFields(provisionInfo.docType).forEach { field ->
            addField(field)
            if (field.name in nameElements) {
                holderNameEditText = binding.layoutSelfSignedDetails.findViewById(field.id)
            }
        }
        setupTextChangeListener()

        vm.loading.observe(viewLifecycleOwner) {
            binding.loadingProgress.visibility = it
        }

        vm.created.observe(viewLifecycleOwner) {
            Toast.makeText(
                requireContext(),
                "Document created successfully!",
                Toast.LENGTH_SHORT,
            ).show()
            findNavController().navigate(
                SelfSignedDetailsFragmentDirections.actionSelfSignedDetailsToSelectDocumentFragment(),
            )
        }
    }

    private fun getDocumentNameValue(): String {
        val value = vm.getFields(provisionInfo.docType).find { it.name in nameElements }?.value
        val name = value?.toString() ?: ""
        val docName = provisionInfo.docName
        return if (name.isBlank()) docName else "$name's $docName"
    }

    private fun setupTextChangeListener() {
        holderNameEditText.addTextChangedListener { newValue ->
            documentNameEditText.setText("$newValue's ${provisionInfo.docName}")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        updateList()
    }

    private fun updateList() {
        provisionInfo.docName = documentNameEditText.text.toString()
        vm.getFields(provisionInfo.docType).forEachIndexed { index, field ->
            vm.getFields(provisionInfo.docType)[index] = getField(field)
        }
    }

    private fun getField(field: Field): Field {
        return when (field.fieldType) {
            is DocumentAttributeType.Picture -> {
                Field(
                    field.id,
                    field.label,
                    field.name,
                    field.fieldType,
                    getImageViewValue(field.id),
                    namespace = field.namespace,
                    parentId = field.parentId,
                    stringOptions = field.stringOptions,
                    integerOptions = field.integerOptions,
                )
            }

            is DocumentAttributeType.Boolean -> {
                Field(
                    field.id,
                    field.label,
                    field.name,
                    field.fieldType,
                    getViewValue(field.id),
                    namespace = field.namespace,
                    parentId = field.parentId,
                    stringOptions = field.stringOptions,
                    integerOptions = field.integerOptions,
                )
            }

            else -> {
                Field(
                    field.id,
                    field.label,
                    field.name,
                    field.fieldType,
                    getViewValue(field.id),
                    namespace = field.namespace,
                    isArray = field.isArray,
                    parentId = field.parentId,
                    stringOptions = field.stringOptions,
                    integerOptions = field.integerOptions,
                )
            }
        }
    }

    private fun addField(field: Field) {
        when (field.fieldType) {
            is DocumentAttributeType.Picture -> {
                binding.layoutSelfSignedDetails.addView(
                    getTextView(field.id + 500, field.label),
                )
                binding.layoutSelfSignedDetails.addView(
                    getImageView(
                        field.id,
                        field.value as Bitmap,
                    ) { dispatchTakePictureIntent(field.id) },
                )
            }

            is DocumentAttributeType.Boolean -> {
                binding.layoutSelfSignedDetails.addView(
                    getTextView(field.id + 500, field.label),
                )
                binding.layoutSelfSignedDetails.addView(
                    checkBox(field.id, field.value as Boolean),
                )
            }

            is DocumentAttributeType.String, DocumentAttributeType.Number -> {
                binding.layoutSelfSignedDetails.addView(
                    getTextView(field.id + 500, field.label),
                )
                binding.layoutSelfSignedDetails.addView(
                    getEditView(field.id, field.value.toString(), null),
                )
            }

            is DocumentAttributeType.Date, DocumentAttributeType.DateTime -> {
                binding.layoutSelfSignedDetails.addView(
                    getTextView(field.id + 500, field.label),
                )
                binding.layoutSelfSignedDetails.addView(
                    getEditView(field.id, field.value as String, picker(field.id, field.id + 500)),
                )
            }

            is DocumentAttributeType.IntegerOptions -> {
                binding.layoutSelfSignedDetails.addView(
                    getTextView(field.id + 500, field.label),
                )
                binding.layoutSelfSignedDetails.addView(
                    integerOptionsSpinner(
                        field.integerOptions!!,
                        field.id,
                        field.value,
                    ),
                )
            }

            is DocumentAttributeType.StringOptions -> {
                binding.layoutSelfSignedDetails.addView(
                    getTextView(field.id + 500, field.label),
                )
                binding.layoutSelfSignedDetails.addView(
                    stringOptionsSpinner(
                        field.stringOptions!!,
                        field.id,
                        field.value,
                    ),
                )
            }

            is DocumentAttributeType.ComplexType -> {
                binding.layoutSelfSignedDetails.addView(
                    getTitleView(field.id + 500, field.label),
                )
            }
        }
    }

    private fun getImageView(
        id: Int,
        bitmap: Bitmap,
        onClickListener: View.OnClickListener?,
    ): View {
        val imageView = ImageView(requireContext())
        imageView.id = id
        imageView.setImageBitmap(bitmap)

        imageView.layoutParams =
            LinearLayout.LayoutParams(bitmap.width, bitmap.height).also {
                it.setMargins(16, 16, 16, 0)
            }
        onClickListener?.let {
            imageView.setOnClickListener(it)
        }
        return imageView
    }

    private fun getTextView(
        id: Int,
        value: String,
    ): View {
        val textView = TextView(requireContext())
        textView.id = id
        textView.text = value
        textView.layoutParams =
            LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).also {
                it.setMargins(16, 16, 16, 0)
            }
        return textView
    }

    private fun getTitleView(
        id: Int,
        value: String,
    ): View {
        val textView = TextView(requireContext())
        textView.id = id
        textView.text = value
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18f)
        textView.setTypeface(textView.typeface, Typeface.BOLD)
        textView.layoutParams =
            LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).also {
                it.setMargins(16, 32, 16, 16)
            }
        return textView
    }

    private fun getEditView(
        id: Int,
        value: String,
        onClickListener: View.OnClickListener?,
    ): View {
        val editText = EditText(requireContext())
        editText.id = id
        editText.text = Editable.Factory.getInstance().newEditable(value)
        editText.layoutParams =
            LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).also {
                it.setMargins(16, 0, 16, 0)
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
        val dData =
            SelfSignedDocumentData(
                provisionInfo,
                vm.getFields(provisionInfo.docType),
            )
        vm.createSelfSigned(dData)
        binding.loadingProgress.visibility = View.VISIBLE
    }

    private fun getImageViewValue(id: Int): Bitmap {
        val imageView = binding.layoutSelfSignedDetails.findViewById<ImageView>(id)
        val bitmap =
            Bitmap.createBitmap(
                imageView.width,
                imageView.height,
                Bitmap.Config.ARGB_8888,
            )
        val canvas = Canvas(bitmap)
        imageView.draw(canvas)
        return bitmap
    }

    private fun getViewValue(id: Int): Any? {
        return when (val view = binding.layoutSelfSignedDetails.findViewById<View>(id)) {
            is CheckBox -> {
                view.isChecked
            }

            is TextView -> {
                view.text.toString()
            }

            is Spinner -> {
                when (view.selectedItem) {
                    is StringOption -> (view.selectedItem as StringOption).value
                    is IntegerOption -> (view.selectedItem as IntegerOption).value
                    else -> view.selectedItem.toString()
                }
            }

            else -> {
                String()
            }
        }
    }

    private fun setViewValue(
        id: Int,
        value: String,
    ) {
        val view = binding.layoutSelfSignedDetails.findViewById<View>(id)
        if (view is TextView) {
            view.text = value
        }
    }

    /**
     * OnClickListener for date picker
     */
    private fun picker(
        id: Int,
        idLabel: Int,
    ) = View.OnClickListener {
        val titleText = getViewValue(idLabel) as String
        val dateText = getViewValue(id) as String
        log("$dateText - ${fullDateStringToMilliseconds(dateText)}")
        val datePicker =
            MaterialDatePicker.Builder.datePicker().setTitleText(titleText)
                .setSelection(fullDateStringToMilliseconds(dateText)).build()
        datePicker.addOnPositiveButtonClickListener {
            log("$it - ${millisecondsToFullDateString(it)}")
            setViewValue(id, millisecondsToFullDateString(it))
        }
        datePicker.show(parentFragmentManager, view?.tag?.toString())
    }

    private fun stringOptionsSpinner(
        options: List<StringOption>,
        id: Int,
        value: Any?,
    ): View {
        val spinner = Spinner(context)
        spinner.id = id
        val adapter =
            ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, options)
        spinner.adapter = adapter
        val selected = options.find { (it.value == null && value == null) || it.value == value }
        if (selected != null) {
            spinner.setSelection(options.indexOf(selected))
        }
        return spinner
    }

    private fun integerOptionsSpinner(
        options: List<IntegerOption>,
        id: Int,
        value: Any?,
    ): View {
        val spinner = Spinner(context)
        spinner.id = id
        val adapter =
            ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, options)
        spinner.adapter = adapter
        val selected = options.find { (it.value == null && value == null) || it.value == value }
        if (selected != null) {
            spinner.setSelection(options.indexOf(selected))
        }
        return spinner
    }

    private fun checkBox(
        id: Int,
        value: Boolean,
    ): View {
        val checkBox = CheckBox(context)
        checkBox.id = id
        checkBox.isChecked = value
        return checkBox
    }

    // Following to enable take picture
    private lateinit var photoUri: Uri
    private lateinit var currentPhotoPath: String
    private var imageViewId: Int? = null
    private val takePicture =
        registerForActivityResult(TakePicture()) { isSuccess ->
            if (isSuccess) {
                val rotation = calculateDegrees()
                setPic(rotation)
            }
        }
    private val cameraLauncher =
        registerForActivityResult(RequestPermission()) { granted ->
            if (granted) {
                proceedTakingPhoto()
            }
        }

    private fun dispatchTakePictureIntent(viewId: Int) {
        imageViewId = viewId
        if (!hasCameraAvailable()) return
        if (!canTakePhoto()) return
        cameraLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun proceedTakingPhoto() {
        try {
            val imageFile = createImageFile()
            photoUri = getUriForFile(imageFile)
            takePicture.launch(photoUri)
        } catch (exception: IOException) {
            log("Error capturing image", exception)
        }
    }

    private fun hasCameraAvailable(): Boolean {
        val packageManager = requireContext().packageManager
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            val errorMessage = "This device does not have a camera."
            Toast.makeText(activity, errorMessage, Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun canTakePhoto(): Boolean {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        // Ensure that there's a camera activity to handle the intent
        if (takePictureIntent.resolveActivity(requireContext().packageManager) == null) {
            val errorMessage = "Could not find camera activity."
            Toast.makeText(activity, errorMessage, Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    @Throws(IOException::class)
    private fun createImageFile(): File {
        // Create an image file name
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val storageDir: File? = requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "JPEG_${timeStamp}_", // prefix
            ".jpg", // suffix
            storageDir, // directory
        ).apply {
            currentPhotoPath = absolutePath
        }
    }

    private fun getUriForFile(file: File): Uri {
        val authority = "${requireContext().packageName}.fileprovider"
        return FileProvider.getUriForFile(requireContext(), authority, file)
    }

    private fun calculateDegrees(): Float {
        val inputStream = requireContext().contentResolver.openInputStream(photoUri)
        val exifInterface = ExifInterface(inputStream!!)
        return when (exifInterface.getAttributeInt(TAG_ORIENTATION, ORIENTATION_UNDEFINED)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }.apply {
            inputStream.close()
        }
    }

    private fun setPic(rotation: Float) {
        val id = imageViewId
        if (id == null) {
            logError("No image view id, impossible to set picture")
            return
        }

        val imageView = binding.layoutSelfSignedDetails.findViewById<ImageView>(id)

        // Get the dimensions of the View
        val targetW: Int = imageView.width
        val targetH: Int = imageView.height

        val bmOptions =
            BitmapFactory.Options().apply {
                // Get the dimensions of the bitmap
                inJustDecodeBounds = true

                val photoW: Int = outWidth
                val photoH: Int = outHeight

                // Determine how much to scale down the image
                val scaleFactor: Int = max(1, min(photoW / targetW, photoH / targetH))

                // Decode the image file into a Bitmap sized to fill the View
                inJustDecodeBounds = false
                inSampleSize = scaleFactor
                inPurgeable = true
            }

        val original = BitmapFactory.decodeFile(currentPhotoPath, bmOptions)
        val rotated =
            if (rotation != 0f) {
                val matrix = Matrix()
                matrix.postRotate(rotation)
                Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)
            } else {
                original
            }
        imageView.setImageBitmap(rotated)
    }
}
