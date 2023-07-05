package com.ziroh.zunucamera.camera

import TimerManager
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.hardware.display.DisplayManager
import android.media.ThumbnailUtils
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.PermissionChecker
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.ziroh.zunucamera.BuildConfig
import com.ziroh.zunucamera.CameraMode
import com.ziroh.zunucamera.MainViewModel
import com.ziroh.zunucamera.R
import com.ziroh.zunucamera.databinding.FragmentCameraBinding
import com.ziroh.zunucamera.edit.PhotoEditActivity
import com.ziroh.zunucamera.edit.VideoEditActivity
import com.ziroh.zunucamera.utils.AnimationUtils
import com.ziroh.zunucamera.utils.saveToDrive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Suppress("DEPRECATION")
class CameraFragment : Fragment() {

    private val displayManager by lazy {
        requireContext().getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    }

    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding!!


    private lateinit var cameraExecutor: ExecutorService
    private lateinit var timerManager: TimerManager

    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null

    private var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private var zoomSelector = 1f
    private var flashMode = ImageCapture.FLASH_MODE_AUTO
    private lateinit var selectedAspectRatio: com.ziroh.zunucamera.AspectRatio

    private lateinit var viewModel: MainViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCameraBinding.inflate(inflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]
        viewModel.clearFiles(requireContext())
        binding.viewFinder.post {
            displayId = binding.viewFinder.display.displayId

            lifecycleScope.launch {
                initUI()
                setClickListeners()
            }
        }
        displayManager.registerDisplayListener(displayListener, null)
        observeEvents()
    }

    private fun setClickListeners() {
        binding.imageViewAspectRatio.setOnClickListener {
            viewModel.setAspectRatio()
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

    private fun observeEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.selectedEmailProvider.collectLatest {
                switchCameraMode(it)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.selectedAspectRatio.collectLatest {
                if(this@CameraFragment::selectedAspectRatio.isInitialized) {
                    switchAspectRatio(it)
                }else{
                    selectedAspectRatio = com.ziroh.zunucamera.AspectRatio.RATIO_16_9
                }
            }
        }
    }

    private fun switchAspectRatio(aspectRatio: com.ziroh.zunucamera.AspectRatio) {
        selectedAspectRatio = aspectRatio
        bindCameraUseCase()
    }

    private var selectedCameraMode = CameraMode.PHOTO

    @SuppressLint("RestrictedApi")
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
            binding.timerLayout.isVisible = false
        } else {
            binding.buttonPhotos.setBackgroundResource(R.drawable.unselected_mode_background)
            binding.buttonVideo.setBackgroundResource(R.drawable.selected_mode_background)
            binding.buttonVideo.setTextColor(Color.WHITE)
            binding.buttonPhotos.setTextColor(Color.WHITE)
            binding.imageCaptureButton.setImageResource(R.drawable.ic_video_mode)
            binding.imageViewFlashMode.setImageResource(R.drawable.ic_flash_off)
            binding.imageViewPreview.visibility = View.GONE
            binding.timerLayout.isVisible = true
        }
    }



    private fun takePhoto() {
        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return

        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val file = File(requireActivity().filesDir, "${name}.jpeg")

        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(file)
            .build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun
                        onImageSaved(output: ImageCapture.OutputFileResults) {

                    //save to drive
//                    saveToDrive(file.path)

                    //show preview
                    binding.imageViewPreview.visibility = View.VISIBLE
                    binding.imageViewPreview.setImageURI(output.savedUri)


                    //for testing edit
                    val uri = FileProvider.getUriForFile(
                        requireContext(),
                        BuildConfig.APPLICATION_ID + ".provider",
                        File(file.path)
                    )

                    Intent(requireContext(), PhotoEditActivity::class.java).also {
                        it.data = uri
                        startActivity(it)
                    }
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
        val file = File(requireActivity().filesDir, "${name}.mp4")
        val outputFileOptions = FileOutputOptions.Builder(file).build()

        recording = videoCapture.output
            .prepareRecording(requireContext(), outputFileOptions)
            .apply {
                if (PermissionChecker.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.RECORD_AUDIO
                    ) ==
                    PermissionChecker.PERMISSION_GRANTED
                ) {
                    withAudioEnabled()
                }
            }
            .start(ContextCompat.getMainExecutor(requireContext())) { recordEvent ->
                when (recordEvent) {
                    is VideoRecordEvent.Start -> {
                        setVideoRecordingUI(true)
                    }

                    is VideoRecordEvent.Finalize -> {
                        setVideoRecordingUI(false)
                        if (!recordEvent.hasError()) {
                            //saved successfully.
                            requireActivity().saveToDrive(file.path)

                            //show preview
                            val bitmap = ThumbnailUtils.createVideoThumbnail(
                                file.path,
                                MediaStore.Images.Thumbnails.MINI_KIND
                            )
                            binding.imageViewPreview.visibility = View.VISIBLE
                            binding.imageViewPreview.setImageBitmap(bitmap)

                            val uri = FileProvider.getUriForFile(
                                requireContext(),
                                BuildConfig.APPLICATION_ID + ".provider",
                                File(file.path)
                            )

                            Intent(requireContext(), VideoEditActivity::class.java).also {
                                it.data = uri
                                startActivity(it)
                            }
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

    @SuppressLint("RestrictedApi")
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

    private var displayId: Int = -1
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) = view?.let { view ->
            if (displayId == this@CameraFragment.displayId) {
                imageCapture?.targetRotation = view.display.rotation
            }
        } ?: Unit
    }

    private fun switchCamera() {
        cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
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
        val rotation = binding.viewFinder.display.rotation

        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        val aspectRatio = if(selectedAspectRatio == com.ziroh.zunucamera.AspectRatio.RATIO_16_9){
            AspectRatio.RATIO_16_9
        }else{
            AspectRatio.RATIO_4_3
        }

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder()
                .setTargetRotation(rotation)
                .setTargetAspectRatio(aspectRatio)
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
                .setTargetAspectRatio(aspectRatio)
                .setTargetRotation(rotation)
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

        }, ContextCompat.getMainExecutor(requireContext()))
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
        displayManager.unregisterDisplayListener(displayListener)
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