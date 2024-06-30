package com.chatho.chatransfer.handle

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.chatho.chatransfer.api.API_METHOD
import com.chatho.chatransfer.api.FlaskAPI
import com.chatho.chatransfer.api.GetFileInfoListResponse
import com.chatho.chatransfer.view.MainActivity
import java.io.File

class HandleAPI(private var activity: MainActivity, private var api: FlaskAPI) {
    private val serviceConnection = object : ServiceConnection {
        lateinit var binder: HandleNotification.ServiceBinder
        override fun onServiceConnected(p0: ComponentName?, service: IBinder?) {
            binder = service as HandleNotification.ServiceBinder
            val runningServiceInstance = binder.getServiceInstance()
            api = FlaskAPI(activity, runningServiceInstance)

            when (binder.getApiMethod()) {
                API_METHOD.UPLOAD -> startUpload()

                API_METHOD.DOWNLOAD -> startDownload()
            }
        }

        override fun onServiceDisconnected(p0: ComponentName?) {
            api = FlaskAPI(activity, null)
        }
    }
    var uploadFileList: List<File>? = null
    var downloadFileInfoList: ArrayList<GetFileInfoListResponse>? = null

    internal fun startService(apiMethod: API_METHOD) {
        if (isServiceRunning(HandleNotification::class.java)) {
            serviceConnection.binder.setApiMethod(apiMethod)
            when (apiMethod) {
                API_METHOD.UPLOAD -> startUpload()

                API_METHOD.DOWNLOAD -> startDownload()
            }
        } else {
            val intent = Intent(activity, HandleNotification::class.java)
            intent.putExtra("API_METHOD", apiMethod)
            ContextCompat.startForegroundService(activity, intent)
            activity.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    private fun startUpload() {
        api.uploadFiles(uploadFileList!!)
    }

    private fun startDownload() {
        api.downloadFiles(downloadFileInfoList!!)
    }

    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = activity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }
}