package com.subhajit.phototeditingsoftware

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.view.animation.TranslateAnimation
import android.widget.ImageButton
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        val arrowButton = findViewById<ImageButton>(R.id.continueImageButton)
        val animation  = ObjectAnimator.ofFloat(
            arrowButton,
            "translationX",
            0f,40f
        )
        animation.duration = 1000;
        animation.repeatCount = ValueAnimator.INFINITE;
        animation.repeatMode = ValueAnimator.REVERSE
        animation.start()

        arrowButton.setOnClickListener {
            val intent = Intent(this, EditingPage::class.java);
            startActivity(intent);
        }
    }
}