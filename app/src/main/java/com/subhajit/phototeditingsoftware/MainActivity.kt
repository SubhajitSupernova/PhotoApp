package com.subhajit.phototeditingsoftware

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // Bind Views
        val continueBtn = findViewById<MaterialCardView>(R.id.continueBtn)
        val arrowIcon = findViewById<ImageView>(R.id.btnArrow)
        val textContent = findViewById<View>(R.id.textContent)

        // 1. Initial State for Fade-in (Professional Entrance)
        continueBtn.alpha = 0f
        continueBtn.translationY = 100f
        textContent.alpha = 0f

        // 2. Entrance Animations
        textContent.animate().alpha(1f).setDuration(1000).start()
        continueBtn.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(800)
            .setStartDelay(500)
            .start()

        // 3. Arrow Pulse Animation (Your original logic, improved)
        val animation = ObjectAnimator.ofFloat(
            arrowIcon,
            "translationX",
            0f, 20f
        )
        animation.duration = 800
        animation.repeatCount = ValueAnimator.INFINITE
        animation.repeatMode = ValueAnimator.REVERSE
        animation.start()

        // 4. Navigation
        continueBtn.setOnClickListener {
            val intent = Intent(this, EditingPage::class.java)
            startActivity(intent)
            // Modern fade transition
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }
}