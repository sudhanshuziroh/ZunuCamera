package com.ziroh.zunucamera

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {
    fun clearFiles(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            context.filesDir.listFiles()?.toList()?.filter {
                it.name != "profileInstalled" && !it.isDirectory
            }?.map {
              it.delete()
            }
            context.cacheDir.listFiles()?.map {
                it.delete()
            }
        }
    }
}