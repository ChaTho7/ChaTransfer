package com.chatho.chatransfer.handle

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.chatho.chatransfer.R
import com.chatho.chatransfer.api.FlaskAPI
import com.chatho.chatransfer.api.UploadFilesProgressRequestBody
import com.chatho.chatransfer.holder.DownloadFilesProgressHolder
import java.io.File


class HandleNotification() : Service() {
    private val flaskAPI = FlaskAPI(this)
    private lateinit var notificationBuilder: NotificationCompat.Builder
    private lateinit var notificationManager: NotificationManager

    private fun setupNotification(fileName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "chatransfer"
            val channelName = "ChaTransfer Channel"
            val channel =
                NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_DEFAULT)

            notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)

            notificationBuilder =
                NotificationCompat.Builder(this, channelId).setContentTitle(fileName)
                    .setSmallIcon(R.mipmap.ic_launcher_foreground).setSilent(true)
                    .setColor(ContextCompat.getColor(this, R.color.black))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val apiMethod = intent!!.getStringExtra("apiMethod")!!
        val fileNames = intent.getStringArrayListExtra("fileNames")!!

        setupNotification(fileNames[0])

        when (apiMethod) {
            "downloadFiles" -> flaskAPI.downloadFiles(fileNames)

            "uploadFiles" -> flaskAPI.uploadFiles(HandleFileSystem.uploadFileList!!)

            else -> println("UNKNOWN API METHOD")
        }

        return START_STICKY
    }

    fun startForeground() {
        startForeground(FOREGROUND_ID, notificationBuilder.build())
    }

    fun updateForeground(apiMethod: String, progress: Int, fileName: String, currentIndex: Int) {
        when (apiMethod) {
            "uploadFiles" -> notificationBuilder.setSubText("Uploading ${currentIndex + 1} of ${UploadFilesProgressRequestBody.totalFilesSize}")
            "downloadFiles" -> notificationBuilder.setSubText("Downloading ${currentIndex + 1} of ${DownloadFilesProgressHolder.totalFilesSize}")
        }
        notificationBuilder.setContentTitle(fileName)
        notificationBuilder.setProgress(100, progress, false)
        notificationManager.notify(FOREGROUND_ID, notificationBuilder.build())
    }

    fun finishForeground(apiMethod: String, filenames: List<String>) {
        notificationBuilder.setSubText(null)
        when (apiMethod) {
            "uploadFiles" -> {
                notificationBuilder.setContentTitle("${if (filenames.size > 1) "${UploadFilesProgressRequestBody.totalFilesSize} Files" else "File"} Uploaded Successfully")
                notificationBuilder.setStyle(
                    NotificationCompat.BigTextStyle().bigText(filenames.joinToString("\n"))
                )
            }

            "downloadFiles" -> {
                notificationBuilder.setContentTitle("${if (filenames.size > 1) "${DownloadFilesProgressHolder.totalFilesSize} Files" else "File"} Downloaded Successfully")
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_DETACH)
            }
            FOREGROUND_ID += 1
        }, 250)
    }

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }

    companion object {
        private var FOREGROUND_ID = 1
        fun startUploadFiles(context: Context, fileList: List<File>) {
            val intent = Intent(context, HandleNotification::class.java)
            intent.putExtra("fileNames", arrayListOf(fileList[0].name))
            intent.putExtra("apiMethod", "uploadFiles")
            ContextCompat.startForegroundService(context, intent)
        }

        fun startDownloadFiles(context: Context, files: ArrayList<String>) {
            val intent = Intent(context, HandleNotification::class.java)
            intent.putExtra("fileNames", files)
            intent.putExtra("apiMethod", "downloadFiles")
            ContextCompat.startForegroundService(context, intent)
        }
    }

}