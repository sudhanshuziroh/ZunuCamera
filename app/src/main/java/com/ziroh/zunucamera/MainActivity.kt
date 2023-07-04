package com.ziroh.zunucamera

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.ziroh.zunucamera.databinding.ActivityMainBinding
import com.ziroh.zunucamera.utils.hideSystemUI

enum class CameraMode {
    PHOTO, VIDEO
}

enum class AspectRatio {
    RATIO_16_9, RATIO_4_3
}

private const val IMMERSIVE_FLAG_TIMEOUT = 500L

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }

    override fun onResume() {
        super.onResume()
        binding.fragmentContainer.postDelayed({
            this@MainActivity.hideSystemUI(binding.fragmentContainer)
        }, IMMERSIVE_FLAG_TIMEOUT)
    }
}