package com.ziroh.zunucamera

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {

    private val _selectedCameraMode = MutableStateFlow(CameraMode.PHOTO)
    val selectedEmailProvider: StateFlow<CameraMode> = _selectedCameraMode

    fun setCameraMode(mode: CameraMode) {
        _selectedCameraMode.value = mode
    }

    fun clearFiles(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            context.filesDir.listFiles()?.toList()?.filter {
                it.name != "profileInstalled"
            }?.map {
              it.delete()
            }
        }
    }
}