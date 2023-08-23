package com.ziroh.zunucamera

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.ziroh.zunucamera.databinding.ActivityMainBinding
import com.ziroh.zunucamera.utils.hideSystemUI

enum class CameraMode {
    PHOTO, VIDEO
}

enum class AspectRatio {
    RATIO_4_3, RATIO_16_9, RATIO_FULL_SCREEN, RATIO_1_1
}

private const val IMMERSIVE_FLAG_TIMEOUT = 500L

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        viewModel.clearFiles(this)
        setContentView(binding.root)
    }

    override fun onResume() {
        super.onResume()
        binding.fragmentContainer.postDelayed({
            this@MainActivity.hideSystemUI(binding.fragmentContainer)
        }, IMMERSIVE_FLAG_TIMEOUT)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        PermissionHandler().onRequestPermissionsResult(requestCode, grantResults)
    }
}