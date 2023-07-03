package com.ziroh.zunucamera.utils

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream

object FileUtils {
    suspend fun copyUriContent(sourceUri: Uri, destinationUri: Uri, contentResolver: ContentResolver) {
        var inputStream: InputStream? = null
        var outputStream: OutputStream? = null
        try {
            inputStream = contentResolver.openInputStream(sourceUri)
            outputStream = contentResolver.openOutputStream(destinationUri)
            if (inputStream != null && outputStream != null) {
                val buffer = ByteArray(1024)
                var length: Int
                while (withContext(Dispatchers.IO) {
                        inputStream.read(buffer)
                    }.also { length = it } > 0) {
                    withContext(Dispatchers.IO) {
                        outputStream.write(buffer, 0, length)
                    }
                }
            }
        } finally {
            withContext(Dispatchers.IO){
                inputStream?.close()
                outputStream?.close()

                val sourceFile = sourceUri.path?.let { File(it) }
                sourceFile?.delete()
            }
        }
    }
}


