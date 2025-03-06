package com.chatho.chatransfer.handle

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class HandlePermission(private val activity: AppCompatActivity) {
    private val requestManageStorage =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            requestManageStorageCallback()
        }

    companion object {
        private const val TAG = "MainActivity"
        private const val PERMISSION_REQUESTS = 1

        private val REQUIRED_RUNTIME_PERMISSIONS =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) arrayOf(
                Manifest.permission.MANAGE_EXTERNAL_STORAGE,
                Manifest.permission.FOREGROUND_SERVICE,
                Manifest.permission.POST_NOTIFICATIONS
            ) else arrayOf(
                Manifest.permission.MANAGE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.FOREGROUND_SERVICE,
                Manifest.permission.POST_NOTIFICATIONS
            )
    }

    fun allRuntimePermissionsGranted(): Boolean {
        for (permission in REQUIRED_RUNTIME_PERMISSIONS) {
            permission.let {
                if (!isPermissionGranted(activity, it)) {
                    return false
                }
            }
        }
        return true
    }

    fun getRuntimePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.data = Uri.parse("package:${activity.packageName}")
            requestManageStorage.launch(intent)
        }

        val permissionsToRequest = ArrayList<String>()
        for (permission in REQUIRED_RUNTIME_PERMISSIONS) {
            permission.let {
                if (!isPermissionGranted(activity, it)) {
                    permissionsToRequest.add(permission)
                }
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                activity, permissionsToRequest.toTypedArray(), PERMISSION_REQUESTS
            )
        }
    }

    private fun requestManageStorageCallback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            Toast.makeText(activity, "You have to give access to all files.", Toast.LENGTH_SHORT)
                .show()
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.data = Uri.parse("package:${activity.packageName}")
            requestManageStorage.launch(intent)
        }
    }

    private fun isPermissionGranted(context: Context, permission: String): Boolean {
        if (permission == Manifest.permission.MANAGE_EXTERNAL_STORAGE) {
            return if (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Environment.isExternalStorageManager()
                } else {
                    true
                }
            ) {
                Log.i(TAG, "Permission granted: $permission")
                true
            } else {
                Toast.makeText(activity, "Permission NOT granted: $permission", Toast.LENGTH_SHORT)
                    .show()
                Log.i(TAG, "Permission NOT granted: $permission")
                false
            }
        }

        if (ContextCompat.checkSelfPermission(
                context, permission
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            Log.i(TAG, "Permission granted: $permission")
            return true
        }
        Toast.makeText(activity, "Permission NOT granted: $permission", Toast.LENGTH_SHORT).show()
        Log.i(TAG, "Permission NOT granted: $permission")
        return false
    }
}