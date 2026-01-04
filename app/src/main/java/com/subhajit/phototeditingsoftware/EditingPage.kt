package com.subhajit.phototeditingsoftware

import android.app.Activity
import android.content.Intent
import android.graphics.*
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import java.io.File
import java.io.FileOutputStream

class EditingPage : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private lateinit var imageCard: MaterialCardView
    private lateinit var sizeDropdown: AutoCompleteTextView
    private lateinit var copyPicker: NumberPicker
    private lateinit var exportBtn: MaterialButton
    private lateinit var btnBrightness: Button
    private lateinit var btnRemoveBg: Button

    private var modifiedBitmap: Bitmap? = null
    private var loadingDialog: AlertDialog? = null

    private val REQUEST_IMAGE_PICK = 100
    private val documentSizes = mapOf(
        "Indian Passport (35x45 mm)" to Pair(35f, 45f),
        "Indian PAN Card (25x35 mm)" to Pair(25f, 35f),
        "US Visa (51x51 mm)" to Pair(50.8f, 50.8f),
        "Stamp Size (20x25 mm)" to Pair(20f, 25f),
        "Schengen Visa (35x45 mm)" to Pair(35f, 45f)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_editing_page)
        initViews()
        setupListeners()
    }

    private fun initViews() {
        imageView = findViewById(R.id.selectedImage)
        imageCard = findViewById(R.id.imageCard)
        sizeDropdown = findViewById(R.id.sizeDropdown)
        copyPicker = findViewById(R.id.copyPicker)
        exportBtn = findViewById(R.id.exportBtn)
        btnBrightness = findViewById(R.id.btnBrightness)
        btnRemoveBg = findViewById(R.id.btnRemoveBg)

        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, documentSizes.keys.toList())
        sizeDropdown.setAdapter(adapter)
        sizeDropdown.setText(documentSizes.keys.first(), false)

        copyPicker.minValue = 1
        copyPicker.maxValue = 36
    }

    private fun setupListeners() {
        imageCard.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(intent, REQUEST_IMAGE_PICK)
        }

        // Find the btnBrightness listener inside EditingPage.kt and update it to this:

        btnBrightness.setOnClickListener {
            modifiedBitmap?.let { bitmap ->
                BrightnessHelper(this).showBrightnessDialog(bitmap,
                    onUpdate = { colorFilter, scale ->
                        imageView.colorFilter = colorFilter

                        // CRITICAL FIX: Ensure the preview uses CENTER_CROP so the
                        // edges outside the visible area are what gets cut.
                        imageView.scaleType = ImageView.ScaleType.CENTER_CROP

                        // We scale the view itself to show the user what's being "zoomed in"
                        imageView.scaleX = scale
                        imageView.scaleY = scale
                    },
                    onFinalize = { finalBitmap ->
                        // This updates the global modifiedBitmap with the newly cropped one
                        updateImage(finalBitmap)
                    }
                )
            } ?: showToast("Upload an image first")
        }

        btnRemoveBg.setOnClickListener {
            modifiedBitmap?.let { runBackgroundRemoval(it) } ?: showToast("Upload an image first")
        }

        exportBtn.setOnClickListener {
            modifiedBitmap?.let { bitmap ->
                val sizeLabel = sizeDropdown.text.toString()
                val dims = documentSizes[sizeLabel] ?: Pair(35f, 45f)
                val file = generatePrintSheet(bitmap, dims.first, dims.second, copyPicker.value)
                if (file != null) shareImage(file)
            } ?: showToast("No image to export")
        }
    }

    private fun runBackgroundRemoval(source: Bitmap) {
        toggleLoading(true)
        // Fixed: Use setDetectorMode instead of setDetectionMode
        val options = SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
            .build()

        val segmenter = Segmentation.getClient(options)
        val inputImage = InputImage.fromBitmap(source, 0)

        segmenter.process(inputImage)
            .addOnSuccessListener { segmentationMask ->
                val mask = segmentationMask.buffer
                val maskWidth = segmentationMask.width
                val maskHeight = segmentationMask.height

                val output = source.copy(Bitmap.Config.ARGB_8888, true)
                val pixels = IntArray(maskWidth * maskHeight)
                source.getPixels(pixels, 0, maskWidth, 0, 0, maskWidth, maskHeight)

                for (i in 0 until maskWidth * maskHeight) {
                    val confidence = mask.float
                    // If confidence is low, it's background -> turn white
                    if (confidence < 0.85) {
                        pixels[i] = Color.WHITE
                    }
                }
                output.setPixels(pixels, 0, maskWidth, 0, 0, maskWidth, maskHeight)
                updateImage(output)
                toggleLoading(false)
            }
            .addOnFailureListener {
                toggleLoading(false)
                showToast("Failed to remove background")
            }
    }

    private fun updateImage(newBitmap: Bitmap) {
        val old = modifiedBitmap
        modifiedBitmap = newBitmap
        imageView.setImageBitmap(modifiedBitmap)
        // Reset preview states
        imageView.apply {
            colorFilter = null
            scaleX = 1.0f
            scaleY = 1.0f
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        if (old != null && old != newBitmap) old.recycle()
    }

    private fun generatePrintSheet(bitmap: Bitmap, wMm: Float, hMm: Float, count: Int): File? {
        val dpi = 300
        val a4W = 2480
        val a4H = 3508
        val sheet = Bitmap.createBitmap(a4W, a4H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawColor(Color.WHITE)

        val itemW = ((wMm / 25.4f) * dpi).toInt()
        val itemH = ((hMm / 25.4f) * dpi).toInt()
        val scaledItem = Bitmap.createScaledBitmap(bitmap, itemW, itemH, true)

        var currentX = 100
        var currentY = 150
        val margin = 45


        for (i in 0 until count) {
            canvas.drawBitmap(scaledItem, currentX.toFloat(), currentY.toFloat(), null)
            currentX += itemW + margin
            if (currentX + itemW > a4W) {
                currentX = 100
                currentY += itemH + margin
            }
            if (currentY + itemH > a4H) break
        }

        return try {
            val file = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "Passport_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { out -> sheet.compress(Bitmap.CompressFormat.JPEG, 100, out) }
            Toast.makeText(this, "Print sheet saved: ${file.name}", Toast.LENGTH_SHORT).show()

            file

        } catch (e: Exception) { null }

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_IMAGE_PICK && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                val raw = MediaStore.Images.Media.getBitmap(contentResolver, uri)
                updateImage(raw.copy(Bitmap.Config.ARGB_8888, true))
            }
        }
    }

    private fun toggleLoading(show: Boolean) {
        if (show) {
            val view = LayoutInflater.from(this).inflate(R.layout.dialog_loading, null)
            loadingDialog = AlertDialog.Builder(this).setView(view).setCancelable(false).create()
            loadingDialog?.show()
        } else loadingDialog?.dismiss()
    }

    private fun shareImage(file: File) {
        val uri = FileProvider.getUriForFile(this, "$packageName.provider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share/Print Sheet"))
    }

    private fun showToast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}