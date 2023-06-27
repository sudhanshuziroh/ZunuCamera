package com.ziroh.zunucamera.utils

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.ziroh.zunucamera.BuildConfig
import java.io.File

@SuppressLint("HardwareIds")
fun Context.saveToDrive(filePath: String) {
    try {
        val intent = Intent()
        intent.component = ComponentName(
            "com.ziroh.zunudrive",
            "com.ziroh.zunudrive.ZunuFileUploadService"
        )
        val uri = FileProvider.getUriForFile(
            this,
            BuildConfig.APPLICATION_ID + ".provider",
            File(filePath)
        )
        this.grantUriPermission("com.ziroh.zunudrive", uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.data = uri
        intent.putExtra("app_id", getSHA1Fingerprint(this))
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startForegroundService(intent)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

@Suppress("DEPRECATION")
@SuppressLint("PackageManagerGetSignatures")
private fun getSHA1Fingerprint(context: Context): String {
    return try {
        context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_SIGNATURES
        ).signatures[0].toCharsString()
    }catch (_: Exception){
        ""
    }
}

fun Activity.hideSystemIcons() {
    val windowInsetsController =
        WindowCompat.getInsetsController(window, window.decorView)
    windowInsetsController.systemBarsBehavior =
        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    window.decorView.setOnApplyWindowInsetsListener { view, windowInsets ->
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        view.onApplyWindowInsets(windowInsets)
    }
}