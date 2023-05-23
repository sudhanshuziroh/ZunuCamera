package com.ziroh.zunucamera

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class MainViewModel: ViewModel() {

    private val _selectedCameraMode = MutableStateFlow(CameraMode.PHOTO)
    val selectedEmailProvider: StateFlow<CameraMode> = _selectedCameraMode

    fun setCameraMode(mode: CameraMode) {
        _selectedCameraMode.value = mode
    }

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording
}