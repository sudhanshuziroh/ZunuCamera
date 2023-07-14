package com.ziroh.zunucamera.camera

import TimerManager
import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.media.ThumbnailUtils
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.video.OnVideoSavedCallback
import androidx.camera.view.video.OutputFileOptions
import androidx.camera.view.video.OutputFileResults
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.ziroh.zunucamera.CameraMode
import com.ziroh.zunucamera.MainViewModel
import com.ziroh.zunucamera.R
import com.ziroh.zunucamera.databinding.FragmentCameraBinding
import com.ziroh.zunucamera.utils.AnimationUtils
import com.ziroh.zunucamera.utils.currentDeviceRealSize
import com.ziroh.zunucamera.utils.formatNumber
import com.ziroh.zunucamera.utils.saveToDrive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Suppress("DEPRECATION")
class CameraFragment : Fragment() {

    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding!!

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var timerManager: TimerManager

    private var zoomSelector = 1f
    private var flashMode = ImageCapture.FLASH_MODE_AUTO
    private var selectedCameraMode = CameraMode.PHOTO
    private var selectedAspectRatio = com.ziroh.zunucamera.AspectRatio.RATIO_FULL_SCREEN
    private var isRecording = false


    private lateinit var viewModel: MainViewModel
    private lateinit var controller: LifecycleCameraController


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCameraBinding.inflate(inflater)
        return binding.root
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]
        binding.viewFinder.post {
            lifecycleScope.launch {
                initUI()
                setClickListeners()

                controller.zoomState.observe(requireActivity()) {
                    val zoomRatio = it.zoomRatio.formatNumber()
                    binding.textViewZoom.text = "${zoomRatio}X"
                }
            }
        }
    }

    @androidx.annotation.OptIn(androidx.camera.view.video.ExperimentalVideo::class)
    private fun setClickListeners() {
        binding.imageViewAspectRatio.setOnClickListener {
            switchAspectRatio()
        }
        binding.imageViewFlashMode.setOnClickListener {
            switchFlash()
        }

        binding.imageViewCameraSwitch.setOnClickListener {
            AnimationUtils.animateCameraIcon(binding.imageViewCameraSwitch)
            switchCamera()
        }

        binding.imageCaptureButton.setOnClickListener {
            if (selectedCameraMode == CameraMode.PHOTO) {
                viewLifecycleOwner.lifecycleScope.launch {
                    binding.layoutShutter.visibility = View.VISIBLE
                    delay(100.milliseconds)
                    binding.layoutShutter.visibility = View.GONE
                }
                controller.setEnabledUseCases(CameraController.IMAGE_CAPTURE)
                captureImage()
            } else {
                controller.setEnabledUseCases(CameraController.VIDEO_CAPTURE)
                captureVideo()
            }
        }

        binding.buttonPhotos.setOnClickListener {
            switchCameraMode(CameraMode.PHOTO)
        }

        binding.buttonVideo.setOnClickListener {
            switchCameraMode(CameraMode.VIDEO)
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
                    viewLifecycleOwner.lifecycleScope.launch {
                        delay(3.seconds)
                        binding.lottieAnimation2.visibility = View.GONE
                    }
                } catch (e: Exception) {
                    binding.lottieAnimation2.visibility = View.GONE
                    Toast.makeText(requireContext(), "Zunu Drive not installed", Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }
    }

    private fun switchAspectRatio() {
        val (width, height) = requireActivity().windowManager.currentDeviceRealSize()
        if (selectedCameraMode == CameraMode.PHOTO) {
            when (selectedAspectRatio) {
                com.ziroh.zunucamera.AspectRatio.RATIO_16_9 -> {
                    controller.imageCaptureTargetSize = CameraController.OutputSize(
                        Size(
                            width,
                            height
                        )
                    )
                    selectedAspectRatio = com.ziroh.zunucamera.AspectRatio.RATIO_FULL_SCREEN
                    binding.imageViewAspectRatio.setImageResource(R.drawable.ic_aspect_full)
                }

                com.ziroh.zunucamera.AspectRatio.RATIO_FULL_SCREEN -> {
                    controller.imageCaptureTargetSize =
                        CameraController.OutputSize(AspectRatio.RATIO_4_3)
                    selectedAspectRatio = com.ziroh.zunucamera.AspectRatio.RATIO_4_3
                    binding.imageViewAspectRatio.setImageResource(R.drawable.ic_4_3)
                }

                com.ziroh.zunucamera.AspectRatio.RATIO_1_1 -> {

                }

                else -> {
                    controller.imageCaptureTargetSize =
                        CameraController.OutputSize(AspectRatio.RATIO_16_9)
                    selectedAspectRatio = com.ziroh.zunucamera.AspectRatio.RATIO_16_9
                    binding.imageViewAspectRatio.setImageResource(R.drawable.ic_16_9)
                }
            }
        } else {
            when (selectedAspectRatio) {
                com.ziroh.zunucamera.AspectRatio.RATIO_16_9 -> {
                    controller.imageCaptureTargetSize =
                        CameraController.OutputSize(AspectRatio.RATIO_4_3)
                    selectedAspectRatio = com.ziroh.zunucamera.AspectRatio.RATIO_4_3
                    binding.imageViewAspectRatio.setImageResource(R.drawable.ic_4_3)
                }

                com.ziroh.zunucamera.AspectRatio.RATIO_4_3 -> {
                    controller.imageCaptureTargetSize =
                        CameraController.OutputSize(AspectRatio.RATIO_16_9)
                    selectedAspectRatio = com.ziroh.zunucamera.AspectRatio.RATIO_16_9
                    binding.imageViewAspectRatio.setImageResource(R.drawable.ic_16_9)
                }

                else -> Unit
            }
        }
    }


    @SuppressLint("RestrictedApi")
    private fun switchCameraMode(mode: CameraMode) {
        selectedCameraMode = mode
        if (mode == CameraMode.PHOTO) {
            flashMode = ImageCapture.FLASH_MODE_AUTO
            controller.imageCaptureFlashMode = ImageCapture.FLASH_MODE_AUTO
            binding.imageViewFlashMode.setImageResource(R.drawable.ic_flash_auto)

            binding.buttonPhotos.setBackgroundResource(R.drawable.selected_mode_background)
            binding.buttonVideo.setBackgroundResource(R.drawable.unselected_mode_background)
            binding.imageCaptureButton.setImageResource(R.drawable.shutter_icon_selector)

            binding.buttonPhotos.setTextColor(Color.WHITE)
            binding.buttonVideo.setTextColor(Color.WHITE)

            controller.enableTorch(false)
            binding.imageViewPreview.visibility = View.VISIBLE
            binding.timerLayout.isVisible = false

            val (width, height) = requireActivity().windowManager.currentDeviceRealSize()
            controller.imageCaptureTargetSize = CameraController.OutputSize(
                Size(
                    width,
                    height
                )
            )
            selectedAspectRatio = com.ziroh.zunucamera.AspectRatio.RATIO_FULL_SCREEN
            binding.imageViewAspectRatio.setImageResource(R.drawable.ic_aspect_full)
        } else {
            flashMode = ImageCapture.FLASH_MODE_OFF
            controller.imageCaptureFlashMode = ImageCapture.FLASH_MODE_OFF
            binding.buttonPhotos.setBackgroundResource(R.drawable.unselected_mode_background)
            binding.buttonVideo.setBackgroundResource(R.drawable.selected_mode_background)
            binding.buttonVideo.setTextColor(Color.WHITE)
            binding.buttonPhotos.setTextColor(Color.WHITE)
            binding.imageCaptureButton.setImageResource(R.drawable.ic_video_mode)
            binding.imageViewFlashMode.setImageResource(R.drawable.ic_flash_off)
            binding.imageViewPreview.visibility = View.GONE
            binding.timerLayout.isVisible = true


            //set aspect ratio to 16_9
            controller.imageCaptureTargetSize =
                CameraController.OutputSize(AspectRatio.RATIO_16_9)
            selectedAspectRatio = com.ziroh.zunucamera.AspectRatio.RATIO_16_9
            binding.imageViewAspectRatio.setImageResource(R.drawable.ic_16_9)
        }
    }


    @androidx.annotation.OptIn(androidx.camera.view.video.ExperimentalVideo::class)
    private fun captureVideo() {
        if (isRecording) {
            controller.stopRecording()
            isRecording = false
        }

        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val file = File(requireActivity().filesDir, "${name}.mp4")
        val outputOptions = OutputFileOptions.builder(file).build()

        controller.startRecording(
            outputOptions,
            ContextCompat.getMainExecutor(requireContext()),
            object : OnVideoSavedCallback {
                override fun onVideoSaved(outputFileResults: OutputFileResults) {
                    setVideoRecordingUI(false)
                    isRecording = false
                    requireActivity().saveToDrive(file.path)
                }

                override fun onError(videoCaptureError: Int, message: String, cause: Throwable?) {
                    controller.stopRecording()
                    isRecording = false
                }
            }
        )
        isRecording = true
        setVideoRecordingUI(true)
    }


    @SuppressLint("RestrictedApi")
    private fun switchFlash() {
        if (selectedCameraMode == CameraMode.PHOTO) {
            when (flashMode) {
                ImageCapture.FLASH_MODE_AUTO -> {
                    flashMode = ImageCapture.FLASH_MODE_ON
                    controller.imageCaptureFlashMode = ImageCapture.FLASH_MODE_ON
                    binding.imageViewFlashMode.setImageResource(R.drawable.ic_flash_on)
                }

                ImageCapture.FLASH_MODE_ON -> {
                    flashMode = ImageCapture.FLASH_MODE_OFF
                    controller.imageCaptureFlashMode = ImageCapture.FLASH_MODE_OFF
                    binding.imageViewFlashMode.setImageResource(R.drawable.ic_flash_off)
                }

                else -> {
                    flashMode = ImageCapture.FLASH_MODE_AUTO
                    controller.imageCaptureFlashMode = ImageCapture.FLASH_MODE_AUTO
                    binding.imageViewFlashMode.setImageResource(R.drawable.ic_flash_auto)
                }
            }
        } else if (selectedCameraMode == CameraMode.VIDEO) {
            flashMode = if (flashMode == ImageCapture.FLASH_MODE_ON) {
                controller.enableTorch(false)
                binding.imageViewFlashMode.setImageResource(R.drawable.ic_flash_off)
                ImageCapture.FLASH_MODE_OFF
            } else {
                controller.enableTorch(true)
                binding.imageViewFlashMode.setImageResource(R.drawable.ic_flash_on)
                ImageCapture.FLASH_MODE_ON
            }
        }
    }

    private fun switchCamera() {
        controller.cameraSelector =
            if (controller.cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }
    }

    private fun changeZoom() {
        when (zoomSelector) {
            1f -> {
                zoomSelector = 2f
                controller.setZoomRatio(2f)
            }

            2f -> {
                zoomSelector = 5f
                controller.setZoomRatio(5f)

            }

            else -> {
                zoomSelector = 1f
                controller.setZoomRatio(1f)
            }
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            requireActivity().baseContext, it
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroyView() {
        _binding = null

        super.onDestroyView()
        cameraExecutor.shutdown()
    }

    @Deprecated("Deprecated in Java")
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
                    requireContext(),
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT
                ).show()
                requireActivity().finish()
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

    private fun startCamera() {
        controller = LifecycleCameraController(requireContext())
        controller.bindToLifecycle((viewLifecycleOwner))
        binding.viewFinder.controller = controller
        val (width, height) = requireActivity().windowManager.currentDeviceRealSize()
        controller.imageCaptureTargetSize = CameraController.OutputSize(
            Size(
                width,
                height
            )
        )
    }

    private fun captureImage() {

        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val file = File(requireActivity().filesDir, "${name}.jpeg")

        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(file)
            .build()

        controller.takePicture(
            outputOptions,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                        requireActivity().saveToDrive(file.path)

                        val rotation = getImageOrientation(file.absolutePath)
                        val bitmap = BitmapFactory.decodeFile(file.absolutePath)

                        val rotatedImage = rotateImage(bitmap, rotation)

                        withContext(Dispatchers.Main) {
                            binding.imageViewPreview.visibility = View.VISIBLE
                            binding.imageViewPreview.setImageBitmap(rotatedImage)
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "onError: ${exception.message}")
                }
            }
        )
    }

    private fun initUI() {
        setPreviewUI()
        timerManager = TimerManager()
        timerManager.setTimerListener(object : TimerManager.TimerListener {
            override fun onTimerTick(elapsedTime: String) {
                binding.textViewTimer.text = elapsedTime
            }
        })
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                requireActivity(), REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }
        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun setVideoRecordingUI(isRecording: Boolean) {
        this.isRecording = isRecording
        binding.timerLayout.isVisible = isRecording
        binding.imageViewCameraSwitch.isEnabled = !isRecording
        binding.constraintLayoutCameraMode.isEnabled = !isRecording

        if (selectedCameraMode == CameraMode.PHOTO) {
            binding.imageViewPreview.isVisible = !isRecording
        }

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
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val cameraPreviewFiles = requireActivity().filesDir.listFiles()?.toList()?.sortedBy {
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
                    withContext(Dispatchers.Main) {
                        binding.imageViewPreview.visibility = View.VISIBLE
                        binding.imageViewPreview.setImageBitmap(bitmap)
                    }
                } else {
                    val rotation = getImageOrientation(lastClickedPreview.absolutePath)
                    val bitmap = BitmapFactory.decodeFile(lastClickedPreview.absolutePath)
                    val rotatedImage = rotateImage(bitmap, rotation)
                    withContext(Dispatchers.Main) {
                        binding.imageViewPreview.visibility = View.VISIBLE
                        binding.imageViewPreview.setImageBitmap(rotatedImage)
                    }
                }
            }
        }
    }


   private fun getImageOrientation(imagePath: String?): Int {
        var rotate = 0
        try {
            val exif = ExifInterface(imagePath!!)
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
                ExifInterface.ORIENTATION_ROTATE_270 -> rotate = 270
                ExifInterface.ORIENTATION_ROTATE_180 -> rotate = 180
                ExifInterface.ORIENTATION_ROTATE_90 -> rotate = 90
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return rotate
    }

    private fun rotateImage(img: Bitmap, degree: Int): Bitmap? {
        val matrix = Matrix()
        matrix.postRotate(degree.toFloat())
        val rotatedImg = Bitmap.createBitmap(img, 0, 0, img.width, img.height, matrix, true)
        img.recycle()
        return rotatedImg
    }
}