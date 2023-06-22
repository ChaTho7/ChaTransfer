package com.chatho.chatransfer.util

import android.Manifest
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class HandlePermission() {
    companion object {
        fun handlePermissions(activity: AppCompatActivity): Boolean {
            val permissions = arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            )
            val requestCode = 1

            val missingPermissions = mutableListOf<String>()

            for (permission in permissions) {
                val result = ContextCompat.checkSelfPermission(activity, permission)
                if (result != PackageManager.PERMISSION_GRANTED) {
                    missingPermissions.add(permission)
                }
            }

            if (missingPermissions.isNotEmpty()) {
                // Request the missing permissions
                activity.requestPermissions(missingPermissions.toTypedArray(), requestCode)
                return false
            }

            // All permissions are granted
            return true
        }
    }

}