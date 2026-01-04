package com.subhajit.phototeditingsoftware

import android.content.Context
import android.graphics.*
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialog

class BrightnessHelper(private val context: Context) {

    private var currentBrightness = 1.0f
    private var currentScale = 1.0f

    /**
     * @param onUpdate: provides (ColorFilter, ScaleFactor) for real-time hardware-accelerated preview
     * @param onFinalize: provides the actual resized/brightened Bitmap once the user clicks Done
     */
    fun showBrightnessDialog(
        currentBitmap: Bitmap,
        onUpdate: (ColorMatrixColorFilter, Float) -> Unit,
        onFinalize: (Bitmap) -> Unit
    ) {
        val dialog = BottomSheetDialog(context)
        val view: View = LayoutInflater.from(context).inflate(R.layout.dialog_brightness, null)

        val brightnessSeekBar = view.findViewById<SeekBar>(R.id.brightnessSeekBar)
        val scaleSeekBar = view.findViewById<SeekBar>(R.id.scaleSeekBar)
        val brightnessText = view.findViewById<TextView>(R.id.brightnessValue)
        val scaleText = view.findViewById<TextView>(R.id.scaleValue)
        val btnDone = view.findViewById<Button>(R.id.btnDone)

        fun updatePreview() {
            // Create a ColorMatrix for brightness logic
            val cm = ColorMatrix(floatArrayOf(
                currentBrightness, 0f, 0f, 0f, 0f,
                0f, currentBrightness, 0f, 0f, 0f,
                0f, 0f, currentBrightness, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            ))
            // Pass the filter and scale back to the activity
            onUpdate(ColorMatrixColorFilter(cm), currentScale)
        }

        brightnessSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(p0: SeekBar?, progress: Int, fromUser: Boolean) {
                currentBrightness = progress / 100f
                brightnessText.text = "$progress%"
                updatePreview()
            }
            override fun onStartTrackingTouch(p0: SeekBar?) {}
            override fun onStopTrackingTouch(p0: SeekBar?) {}
        })

        scaleSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(p0: SeekBar?, progress: Int, fromUser: Boolean) {
                // Adjusting scale range: 0.5x to 2.0x
                currentScale = (progress + 50) / 150f
                scaleText.text = String.format("%.1fx", currentScale)
                updatePreview()
            }
            override fun onStartTrackingTouch(p0: SeekBar?) {}
            override fun onStopTrackingTouch(p0: SeekBar?) {}
        })

        btnDone.setOnClickListener {
            // High-cost operation performed ONLY ONCE here
            val finalResult = applyFinalTransform(currentBitmap, currentBrightness, currentScale)
            onFinalize(finalResult)
            dialog.dismiss()
        }

        dialog.setContentView(view)
        dialog.show()
    }

    private fun applyFinalTransform(src: Bitmap, brightness: Float, scale: Float): Bitmap {
        val newWidth = (src.width * scale).toInt().coerceAtLeast(1)
        val newHeight = (src.height * scale).toInt().coerceAtLeast(1)

        val dest = Bitmap.createBitmap(newWidth, newHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(dest)

        val cm = ColorMatrix(floatArrayOf(
            brightness, 0f, 0f, 0f, 0f,
            0f, brightness, 0f, 0f, 0f,
            0f, 0f, brightness, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))

        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(cm)
        }

        canvas.drawBitmap(src, null, Rect(0, 0, newWidth, newHeight), paint)
        return dest
    }
}