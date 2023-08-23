package com.ziroh.zunucamera

import android.app.Activity
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat

internal class PermissionHandler {
    fun requestPermission(
        activity: Activity, permissions: Array<String>, requestCode: Int,
        listener: RequestPermissionListener?
    ) {
        Companion.requestCode = requestCode
        Companion.listener = listener
        val unGrantedPermissionList: MutableList<String> = ArrayList()
        for (permission in permissions) {
            if (!isPermissionGranted(permission, activity)) {
                unGrantedPermissionList.add(permission)
            }
        }
        val unGrantedPermissions = unGrantedPermissionList.toTypedArray()
        if (unGrantedPermissions.isEmpty()) {
            Companion.listener!!.onSuccess()
            return
        }
        ActivityCompat.requestPermissions(activity, unGrantedPermissions, requestCode)
    }

    private fun isPermissionGranted(permission: String, activity: Activity): Boolean {
        return (ActivityCompat.checkSelfPermission(activity, permission)
                == PackageManager.PERMISSION_GRANTED)
    }

    fun onRequestPermissionsResult(
        requestCode: Int,
        grantResults: IntArray
    ) {
        if (requestCode == Companion.requestCode) {
            if (grantResults.isNotEmpty()) {
                for (grantResult in grantResults) {
                    if (grantResult != PackageManager.PERMISSION_GRANTED) {
                        listener!!.onFailed()
                        return
                    }
                }
                listener!!.onSuccess()
            } else {
                listener!!.onFailed()
            }
        }
    }

    interface RequestPermissionListener {
        fun onSuccess()
        fun onFailed()
    }

    companion object {
        private var listener: RequestPermissionListener? = null
        private var requestCode = 0
    }
}