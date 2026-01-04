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
                // Adjusting scale range: 1.0x (normal) to 3.0x (zoomed in)
                currentScale = 1.0f + (progress / 100f) * 2.0f
                scaleText.text = String.format("%.1fx", currentScale)
                updatePreview()
            }
            override fun onStartTrackingTouch(p0: SeekBar?) {}
            override fun onStopTrackingTouch(p0: SeekBar?) {}
        })

        btnDone.setOnClickListener {
            val result = applyFinalTransform(currentBitmap, currentBrightness, currentScale)
            onFinalize(result)
            dialog.dismiss()
        }

        dialog.setContentView(view)
        dialog.show()
    }

    private fun applyFinalTransform(src: Bitmap, brightness: Float, scale: Float): Bitmap {
        // 1. Calculate the 'Crop' area.
        // If scale is 2.0, we take only 50% of the image width/height from the center.
        val cropWidth = (src.width / scale).toInt()
        val cropHeight = (src.height / scale).toInt()

        // 2. Find the top-left corner of the crop rectangle (centering it)
        val left = (src.width - cropWidth) / 2
        val top = (src.height - cropHeight) / 2

        // 3. Create the output bitmap.
        // We keep the original dimensions so the print quality stays high.
        val dest = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(dest)

        // 4. Apply brightness
        val cm = ColorMatrix(floatArrayOf(
            brightness, 0f, 0f, 0f, 0f,
            0f, brightness, 0f, 0f, 0f,
            0f, 0f, brightness, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ))

        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(cm)
        }

        // 5. Draw the CROPPED section (srcRect) into the FULL destination (destRect)
        val srcRect = Rect(left, top, left + cropWidth, top + cropHeight)
        val destRect = Rect(0, 0, src.width, src.height)

        canvas.drawBitmap(src, srcRect, destRect, paint)

        return dest
    }
}