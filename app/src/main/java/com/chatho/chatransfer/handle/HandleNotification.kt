package com.chatho.chatransfer.handle

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.chatho.chatransfer.R
import com.chatho.chatransfer.Utils
import com.chatho.chatransfer.api.API_METHOD
import com.chatho.chatransfer.api.UploadFilesProgressRequestBody
import com.chatho.chatransfer.holder.DownloadFilesProgressHolder

class HandleNotification : Service() {
    private lateinit var notificationBuilder: NotificationCompat.Builder
    private lateinit var notificationManager: NotificationManager
    private val binder = ServiceBinder()
    private lateinit var apiMethod: API_METHOD

    inner class ServiceBinder : Binder() {
        fun getServiceInstance(): HandleNotification {
            return this@HandleNotification
        }

        fun getApiMethod(): API_METHOD {
            return apiMethod
        }

        fun setApiMethod(newApiMethod: API_METHOD) {
            apiMethod = newApiMethod
        }
    }

    private fun setupNotification() {
        val channelId = "chatransfer"
        val channelName = "ChaTransfer Channel"
        val channel =
            NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_DEFAULT)

        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)

        notificationBuilder = NotificationCompat.Builder(this, channelId).setProgress(100, 0, true)
            .setSmallIcon(R.mipmap.ic_launcher_foreground).setSilent(true)
            .setColor(ContextCompat.getColor(this, R.color.black))

    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        apiMethod = intent!!.getSerializableExtra("API_METHOD") as API_METHOD

        setupNotification()

        return START_STICKY
    }

    fun startForeground() {
        startForeground(FOREGROUND_ID, notificationBuilder.build())
    }

    fun updateForeground(progress: Int, fileName: String, currentIndex: Int) {
        when (apiMethod) {
            API_METHOD.UPLOAD -> notificationBuilder.setSubText("Uploading ${currentIndex + 1} of ${UploadFilesProgressRequestBody.totalFilesCount}")
            API_METHOD.DOWNLOAD -> notificationBuilder.setSubText("Downloading ${currentIndex + 1} of ${DownloadFilesProgressHolder.totalFilesCount}")
        }
        notificationBuilder.setContentTitle("$fileName (% $progress)")
        notificationBuilder.setProgress(100, progress, false)
        notificationManager.notify(FOREGROUND_ID, notificationBuilder.build())
    }

    fun finishForeground(filenames: List<String>) {
        when (apiMethod) {
            API_METHOD.UPLOAD -> {
                val uploadTimeInString =
                    calculateElapsedTime(UploadFilesProgressRequestBody.endTime - UploadFilesProgressRequestBody.startTime)

                notificationBuilder.setSubText(uploadTimeInString)
                notificationBuilder.setContentTitle(
                    "${if (filenames.size > 1) "${UploadFilesProgressRequestBody.totalFilesCount} Files" else "File"} (${
                        Utils.toDouble(
                            UploadFilesProgressRequestBody.totalFilesSize / (1024.0 * 1024.0), 2
                        )
                    } MB) Uploaded Successfully"
                )
                notificationBuilder.setStyle(
                    NotificationCompat.BigTextStyle().bigText(filenames.joinToString("\n"))
                )
            }

            API_METHOD.DOWNLOAD -> {
                val downloadTimeInString =
                    calculateElapsedTime(DownloadFilesProgressHolder.endTime - DownloadFilesProgressHolder.startTime)

                notificationBuilder.setSubText(downloadTimeInString)
                notificationBuilder.setContentTitle(
                    "${if (filenames.size > 1) "${DownloadFilesProgressHolder.totalFilesCount} Files" else "File"} (${
                        Utils.toDouble(
                            DownloadFilesProgressHolder.totalFilesSize / (1024.0 * 1024.0), 2
                        )
                    } MB) Downloaded Successfully"
                )
                notificationBuilder.setStyle(
                    NotificationCompat.BigTextStyle().bigText(filenames.joinToString("\n"))
                )
            }
        }
        notificationBuilder.setProgress(0, 0, false)

        Handler(Looper.getMainLooper()).postDelayed({
            notificationManager.notify(
                FOREGROUND_ID, notificationBuilder.build()
            ) // Put inside postDelayed() to fix not update due to thread stuff...
            stopForeground(STOP_FOREGROUND_DETACH)

            FOREGROUND_ID += 1
        }, 500)
    }

    private fun calculateElapsedTime(timeElapsedInMs: Long): String {
        val timeInMin = timeElapsedInMs / (1000 * 60)
        val timeInSec = (timeElapsedInMs % (1000 * 60)) / 1000
        val timeInMs = timeElapsedInMs % (1000)

        return "${if (timeInMin < 10L) "0$timeInMin" else timeInMin}:" + "${if (timeInSec < 10L) "0$timeInSec" else timeInSec}:" + "${if (timeInMs < 10L) "0$timeInMs" else timeInMs}"
    }

    override fun onBind(p0: Intent?): IBinder {
        return binder
    }

    companion object {
        private var FOREGROUND_ID = 1
    }

}