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

        scaleSeekBar.progress = 0
        brightnessSeekBar.progress = 100

        fun updatePreview() {
            val cm = ColorMatrix(floatArrayOf(
                currentBrightness, 0f, 0f, 0f, 0f,
                0f, currentBrightness, 0f, 0f, 0f,
                0f, 0f, currentBrightness, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            ))
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
                // Range 1.0x to 3.0x
                currentScale = 1.0f + (progress / 100f) * 2.0f
                scaleText.text = String.format("%.1fx", currentScale)
                updatePreview()
            }
            override fun onStartTrackingTouch(p0: SeekBar?) {}
            override fun onStopTrackingTouch(p0: SeekBar?) {}
        })

        btnDone.setOnClickListener {
            // APPLY CROP MATH
            val result = applyFinalTransform(currentBitmap, currentBrightness, currentScale)
            onFinalize(result)
            dialog.dismiss()
        }

        dialog.setContentView(view)
        dialog.show()
    }

    private fun applyFinalTransform(src: Bitmap, brightness: Float, scale: Float): Bitmap {
        // Step 1: Determine the aspect ratio of the crop area
        // If your passport size is 35:45, we ensure we crop that ratio
        val targetWidth = (src.width / scale).toInt()
        val targetHeight = (src.height / scale).toInt()

        // Step 2: Center the crop rect
        val left = (src.width - targetWidth) / 2
        val top = (src.height - targetHeight) / 2
        val right = left + targetWidth
        val bottom = top + targetHeight

        // Step 3: Create the cropped bitmap
        val dest = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(dest)

        val cm = ColorMatrix(floatArrayOf(
            brightness, 0f, 0f, 0f, 0f,
            0f, brightness, 0f, 0f, 0f,
            0f, 0f, brightness, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))

        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(cm)
        }

        val srcRect = Rect(left, top, right, bottom)
        val destRect = Rect(0, 0, targetWidth, targetHeight)

        canvas.drawBitmap(src, srcRect, destRect, paint)
        return dest
    }
}