package com.ziroh.zunucamera

import TimerManager
import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.ThumbnailUtils
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.ziroh.zunucamera.databinding.ActivityMainBinding
import com.ziroh.zunucamera.utils.AnimationUtils
import com.ziroh.zunucamera.utils.hideSystemIcons
import com.ziroh.zunucamera.utils.saveToDrive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

enum class CameraMode {
    PHOTO, VIDEO
}

@Suppress("DEPRECATION")
@SuppressLint("RestrictedApi")
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var timerManager: TimerManager

    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null

    private var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private var zoomSelector = 1f
    private var flashMode = ImageCapture.FLASH_MODE_AUTO

    private lateinit var viewModel: MainViewModel
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        viewModel.clearFiles(this)
        initUI()
        setClickListeners()
        observeEvents()
    }

    private fun setClickListeners() {
        binding.imageViewFlashMode.setOnClickListener {
            switchFlash()
        }

        binding.imageViewCameraSwitch.setOnClickListener {
            AnimationUtils.animateCameraIcon(binding.imageViewCameraSwitch)
            switchCamera()
        }

        binding.imageCaptureButton.setOnClickListener {
            if (selectedCameraMode == CameraMode.PHOTO) {
                lifecycleScope.launch {
                    binding.layoutShutter.visibility = View.VISIBLE
                    delay(100.milliseconds)
                    binding.layoutShutter.visibility = View.GONE
                }
                takePhoto()
            } else {
                captureVideo()
            }
        }

        binding.buttonPhotos.setOnClickListener {
            viewModel.setCameraMode(CameraMode.PHOTO)
        }

        binding.buttonVideo.setOnClickListener {
            viewModel.setCameraMode(CameraMode.VIDEO)
        }

        binding.textViewZoom.setOnClickListener {
            changeZoom()
        }

        binding.imageViewPreview.setOnClickListener {
            if (selectedCameraMode == CameraMode.PHOTO) {
                try {
                    binding.lottieAnimation2.visibility = View.VISIBLE
                    val intent = Intent("com.ziroh.EVENT_ATTACHMENT_REQUEST")
                    intent.putExtra(
                        "com.ziroh.zunudrive.OpenPage",
                        "zunuGallery"
                    )
                    startActivity(intent)
                    lifecycleScope.launch {
                        delay(3.seconds)
                        binding.lottieAnimation2.visibility = View.GONE
                    }
                } catch (e: Exception) {
                    binding.lottieAnimation2.visibility = View.GONE
                    Toast.makeText(this, "Zunu Drive not installed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun observeEvents() {
        lifecycleScope.launch {
            viewModel.selectedEmailProvider.collectLatest {
                switchCameraMode(it)
            }
        }
    }

    private var selectedCameraMode = CameraMode.PHOTO
    private fun switchCameraMode(mode: CameraMode) {
        selectedCameraMode = mode
        if (mode == CameraMode.PHOTO) {
            binding.buttonPhotos.setBackgroundResource(R.drawable.selected_mode_background)
            binding.buttonVideo.setBackgroundResource(R.drawable.unselected_mode_background)
            binding.buttonPhotos.setTextColor(Color.WHITE)
            binding.buttonVideo.setTextColor(Color.WHITE)
            binding.imageCaptureButton.setImageResource(R.drawable.shutter_icon_selector)
            binding.imageViewFlashMode.setImageResource(R.drawable.ic_flash_auto)
            imageCapture?.camera?.cameraControl?.enableTorch(false)
            binding.imageViewPreview.visibility = View.VISIBLE
        } else {
            binding.buttonPhotos.setBackgroundResource(R.drawable.unselected_mode_background)
            binding.buttonVideo.setBackgroundResource(R.drawable.selected_mode_background)
            binding.buttonVideo.setTextColor(Color.WHITE)
            binding.buttonPhotos.setTextColor(Color.WHITE)
            binding.imageCaptureButton.setImageResource(R.drawable.ic_video_mode)
            binding.imageViewFlashMode.setImageResource(R.drawable.ic_flash_off)
            binding.imageViewPreview.visibility = View.GONE
        }
    }

    private fun takePhoto() {
        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return

        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val file = File(filesDir, "${name}.jpeg")

        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(file)
            .build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun
                        onImageSaved(output: ImageCapture.OutputFileResults) {

                    //save to drive
                    saveToDrive(file.path)

                    //show preview
                    binding.imageViewPreview.visibility = View.VISIBLE
                    binding.imageViewPreview.setImageURI(output.savedUri)
                }
            }
        )
    }

    private var isRecording = false
    private fun captureVideo() {
        val videoCapture = this.videoCapture ?: return

        val curRecording = recording
        if (curRecording != null) {
            // Stop the current recording session.
            curRecording.stop()
            recording = null
            return
        }

        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val file = File(filesDir, "${name}.mp4")
        val outputFileOptions = FileOutputOptions.Builder(file).build()

        recording = videoCapture.output
            .prepareRecording(this, outputFileOptions)
            .apply {
                if (PermissionChecker.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.RECORD_AUDIO
                    ) ==
                    PermissionChecker.PERMISSION_GRANTED
                ) {
                    withAudioEnabled()
                }
            }
            .start(ContextCompat.getMainExecutor(this)) { recordEvent ->
                when (recordEvent) {
                    is VideoRecordEvent.Start -> {
                        setVideoRecordingUI(true)
                    }

                    is VideoRecordEvent.Finalize -> {
                        setVideoRecordingUI(false)
                        if (!recordEvent.hasError()) {
                            //saved successfully.
                            saveToDrive(file.path)

                            //show preview
                            val bitmap = ThumbnailUtils.createVideoThumbnail(
                                file.path,
                                MediaStore.Images.Thumbnails.MINI_KIND
                            )
//                            binding.imageViewPreview.visibility = View.VISIBLE
//                            binding.imageViewPreview.setImageBitmap(bitmap)
                        } else {
                            recording?.close()
                            recording = null
                            Log.e(
                                TAG, "Video capture ends with error: " +
                                        "${recordEvent.error}"
                            )
                        }
                    }
                }
            }
    }

    private fun startCamera() {
        bindCameraUseCase()
    }

    private var videoFlashOn = false
    private fun switchFlash() {
        if (selectedCameraMode == CameraMode.PHOTO) {
            when (flashMode) {
                ImageCapture.FLASH_MODE_AUTO -> {
                    flashMode = ImageCapture.FLASH_MODE_ON
                    binding.imageViewFlashMode.setImageResource(R.drawable.ic_flash_on)
                }

                ImageCapture.FLASH_MODE_ON -> {
                    flashMode = ImageCapture.FLASH_MODE_OFF
                    binding.imageViewFlashMode.setImageResource(R.drawable.ic_flash_off)
                }

                else -> {
                    flashMode = ImageCapture.FLASH_MODE_AUTO
                    binding.imageViewFlashMode.setImageResource(R.drawable.ic_flash_auto)
                }
            }
            bindCameraUseCase()
        } else if (selectedCameraMode == CameraMode.VIDEO) {
            if (videoFlashOn) {
                imageCapture?.camera?.cameraControl?.enableTorch(false)
                binding.imageViewFlashMode.setImageResource(R.drawable.ic_flash_off)
            } else {
                imageCapture?.camera?.cameraControl?.enableTorch(true)
                binding.imageViewFlashMode.setImageResource(R.drawable.ic_flash_on)
            }
            videoFlashOn = !videoFlashOn
        }
    }

    private fun switchCamera() {
        cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
//        if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
//            imageCapture?.flashMode = ImageCapture.FLASH_MODE_ON
//        }
        bindCameraUseCase()
    }

    private fun changeZoom() {
        when (zoomSelector) {
            1f -> {
                zoomSelector = 2f
                binding.textViewZoom.text = getString(R.string.zoom_2x)
                camera.cameraControl.setZoomRatio(2f)
            }

            2f -> {
                zoomSelector = 5f
                binding.textViewZoom.text = getString(R.string.zoom_5x)
                camera.cameraControl.setZoomRatio(5f)
            }

            else -> {
                zoomSelector = 1f
                binding.textViewZoom.text = getString(R.string.zoom_1x)
                camera.cameraControl.setZoomRatio(1f)
            }
        }
    }

    private lateinit var camera: Camera
    private fun bindCameraUseCase() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({

            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                }
            val recorder = Recorder.Builder()
                .setQualitySelector(
                    QualitySelector.from(
                        Quality.HIGHEST,
                        FallbackStrategy.higherQualityOrLowerThan(Quality.SD)
                    )
                )
                .build()

            videoCapture = VideoCapture.withOutput(recorder)
            imageCapture = ImageCapture
                .Builder()
                .setFlashMode(flashMode)
                .build()

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture, videoCapture
                )
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(
                    this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    companion object {
        private const val TAG = "CameraXApp"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            mutableListOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }


    private fun initUI() {
        setPreviewUI()
        timerManager = TimerManager()
        timerManager.setTimerListener(object : TimerManager.TimerListener {
            override fun onTimerTick(elapsedTime: String) {
                binding.textViewTimer.text = elapsedTime
            }
        })
        hideSystemIcons()
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }
        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun setVideoRecordingUI(isRecording: Boolean) {
        this.isRecording = isRecording
        binding.timerLayout.isVisible = isRecording
        binding.imageViewCameraSwitch.isEnabled = !isRecording
        binding.constraintLayoutCameraMode.isEnabled = !isRecording
        binding.imageViewPreview.isVisible = !isRecording

        if (isRecording) {
            binding.imageCaptureButton.setImageResource(R.drawable.video_stop)
            timerManager.startTimer()
            binding.imageViewCameraSwitch.alpha = 0f
            binding.constraintLayoutCameraMode.visibility = View.INVISIBLE
        } else {
            binding.imageCaptureButton.setImageResource(R.drawable.ic_video_mode)
            timerManager.stopTimer()
            binding.imageViewCameraSwitch.alpha = 1f
            binding.constraintLayoutCameraMode.visibility = View.VISIBLE
            binding.textViewTimer.text = getString(R.string.recording_time_initial)
        }
    }

    private fun setPreviewUI() {
        lifecycleScope.launch(Dispatchers.IO) {
            val cameraPreviewFiles = filesDir.listFiles()?.toList()?.sortedBy {
                it.lastModified()
            }?.filter {
                it.name != "profileInstalled"
            }

            if (cameraPreviewFiles.isNullOrEmpty()) {
                withContext(Dispatchers.Main) { binding.imageViewPreview.visibility = View.GONE }
            } else {
                val lastClickedPreview = cameraPreviewFiles[cameraPreviewFiles.size - 1]
                if (lastClickedPreview.name.endsWith(".mp4")) {
                    val bitmap = ThumbnailUtils.createVideoThumbnail(
                        lastClickedPreview.absolutePath,
                        MediaStore.Images.Thumbnails.MINI_KIND
                    )
                    binding.imageViewPreview.visibility = View.VISIBLE
                    binding.imageViewPreview.setImageBitmap(bitmap)
                } else {
                    val bitmap = BitmapFactory.decodeFile(lastClickedPreview.absolutePath)
                    withContext(Dispatchers.Main) {
                        binding.imageViewPreview.visibility = View.VISIBLE
                        binding.imageViewPreview.setImageBitmap(bitmap)
                    }
                }
            }
        }
    }
}