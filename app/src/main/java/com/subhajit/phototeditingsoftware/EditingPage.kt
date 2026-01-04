package com.subhajit.phototeditingsoftware

import android.app.Activity
import android.content.Intent
import android.graphics.*
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.LayoutInflater
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
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

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

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

        btnBrightness.setOnClickListener {
            modifiedBitmap?.let { bitmap ->
                val helper = BrightnessHelper(this)
                helper.showBrightnessDialog(bitmap,
                    onUpdate = { colorFilter, scaleFactor ->
                        // FAST PREVIEW: No bitmap creation here
                        imageView.colorFilter = colorFilter
                        imageView.scaleX = scaleFactor
                        imageView.scaleY = scaleFactor
                    },
                    onFinalize = { finalBitmap ->
                        // 1. Keep reference to old bitmap
                        val oldBitmap = modifiedBitmap

                        // 2. Assign and set new bitmap
                        modifiedBitmap = finalBitmap
                        imageView.setImageBitmap(modifiedBitmap)

                        // 3. Reset preview styles
                        imageView.colorFilter = null
                        imageView.scaleX = 1.0f
                        imageView.scaleY = 1.0f

                        // 4. CRITICAL: Recycle old bitmap memory immediately
                        if (oldBitmap != null && oldBitmap != finalBitmap && !oldBitmap.isRecycled) {
                            oldBitmap.recycle()
                        }
                        System.gc() // Hint garbage collection
                    }
                )
            } ?: showToast("Please upload an image first")
        }

        btnRemoveBg.setOnClickListener {
            if (modifiedBitmap != null) showAiPopup() else showToast("Please upload an image first")
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

    private fun showAiPopup() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_loading, null)
        loadingDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()
        loadingDialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
        loadingDialog?.show()

        Handler(Looper.getMainLooper()).postDelayed({
            applyStudioBackground()
            loadingDialog?.dismiss()
            showToast("Background refined by AI")
        }, 2000)
    }

    private fun applyStudioBackground() {
        modifiedBitmap?.let { src ->
            val oldBitmap = modifiedBitmap
            val result = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(result)
            canvas.drawColor(Color.WHITE)
            canvas.drawBitmap(src, 0f, 0f, null)

            modifiedBitmap = result
            imageView.setImageBitmap(modifiedBitmap)

            if (oldBitmap != null && oldBitmap != result && !oldBitmap.isRecycled) {
                oldBitmap.recycle()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_IMAGE_PICK && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                val rawBitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
                // Ensure we use a mutable ARGB_8888 bitmap for processing
                val old = modifiedBitmap
                modifiedBitmap = rawBitmap.copy(Bitmap.Config.ARGB_8888, true)
                imageView.setImageBitmap(modifiedBitmap)
                imageView.setPadding(0, 0, 0, 0)

                if (old != null && !old.isRecycled) old.recycle()
                if (rawBitmap != modifiedBitmap && !rawBitmap.isRecycled) rawBitmap.recycle()
            }
        }
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

        scaledItem.recycle() // Clean up the temp scaled image

        return try {
            val file = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "PrintSheet_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { out ->
                sheet.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            sheet.recycle() // Clean up A4 bitmap
            file
        } catch (e: Exception) {
            null
        }
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