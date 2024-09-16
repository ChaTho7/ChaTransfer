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
    interface CustomServiceConnection : ServiceConnection {
        fun getBinder(): HandleNotification.ServiceBinder?
    }

    val serviceConnection = object : CustomServiceConnection {
        private lateinit var binder: HandleNotification.ServiceBinder

        override fun onServiceConnected(p0: ComponentName?, service: IBinder?) {
            SERVICE_BOUND = true

            binder = service as HandleNotification.ServiceBinder
            val runningServiceInstance = binder.getServiceInstance()
            api = FlaskAPI(activity, runningServiceInstance)

            when (binder.getApiMethod()) {
                API_METHOD.UPLOAD -> startUpload()

                API_METHOD.DOWNLOAD -> startDownload()
            }
        }

        override fun onBindingDied(name: ComponentName?) {
            SERVICE_BOUND = false

            api = FlaskAPI(activity, null)
        }

        override fun onNullBinding(name: ComponentName?) {
            SERVICE_BOUND = false

            api = FlaskAPI(activity, null)
        }

        override fun onServiceDisconnected(p0: ComponentName?) {
            SERVICE_BOUND = false

            api = FlaskAPI(activity, null)
        }

        override fun getBinder(): HandleNotification.ServiceBinder {
            return binder
        }
    }

    var uploadFileList: List<File>? = null
    var downloadFileInfoList: ArrayList<GetFileInfoListResponse>? = null

    internal fun startService(apiMethod: API_METHOD) {
        val intent = Intent(activity, HandleNotification::class.java)
        intent.putExtra("API_METHOD", apiMethod)

        if (!SERVICE_BOUND) {
            if (!isServiceRunning(HandleNotification::class.java)) {
                ContextCompat.startForegroundService(activity, intent)
            } else {
                activity.stopService(intent)
                ContextCompat.startForegroundService(activity, intent)
            }

            activity.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        } else {
            serviceConnection.getBinder()!!.setApiMethod(apiMethod)
            when (apiMethod) {
                API_METHOD.UPLOAD -> startUpload()

                API_METHOD.DOWNLOAD -> startDownload()
            }
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

    companion object {
        var SERVICE_BOUND = false
    }
}