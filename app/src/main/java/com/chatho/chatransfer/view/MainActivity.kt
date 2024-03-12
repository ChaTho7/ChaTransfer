package com.chatho.chatransfer.view

import android.os.Bundle
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.chatho.chatransfer.R
import com.chatho.chatransfer.adapter.SelectedFilesRecyclerAdapter
import com.chatho.chatransfer.adapter.ServerFilesRecyclerAdapter
import com.chatho.chatransfer.api.FlaskAPI
import com.chatho.chatransfer.databinding.ActivityMainBinding
import com.chatho.chatransfer.handle.HandleFileSystem
import com.chatho.chatransfer.handle.HandleFileSystem.Companion.getDownloadsDirectory
import com.chatho.chatransfer.handle.HandleNotification
import com.chatho.chatransfer.handle.HandlePermission
import com.chatho.chatransfer.holder.DownloadFilesProgressHolder
import com.chatho.chatransfer.holder.MainActivityHolder
import java.io.File
import kotlin.reflect.KFunction

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var api: FlaskAPI
    private lateinit var filePicker: HandleFileSystem
    private var handlePermission = HandlePermission(this)
    private lateinit var selectedFilesAdapter: SelectedFilesRecyclerAdapter
    private var serverFilesAdapter: ServerFilesRecyclerAdapter? = null
    private var isServerOnline: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        selectedFilesAdapter = SelectedFilesRecyclerAdapter(arrayListOf(), binding)
        setAppName()
        MainActivityHolder.activity = this
        api = FlaskAPI(null)

        filePicker = HandleFileSystem { fileList ->
            HandleNotification.startUploadFiles(this, fileList)
        }

        if (!handlePermission.allRuntimePermissionsGranted()) {
            handlePermission.getRuntimePermissions()
        }

        getServerStatus(null)

        handleLayouts()
        handleListeners()
    }

    override fun onResume() {
        super.onResume()

        hideSystemBars()
    }

    fun downloadFilesProgressCallback(fileName: String, progress: Int) {
        binding.progress.text = "DOWNLOAD PROGRESS OF $fileName: % $progress"
    }

    fun downloadFilesCallback(fileName: String) {
        binding.progress.text = "PROGRESS: WAITING FOR A REQUEST..."
        Toast.makeText(
            this, "$fileName downloaded", Toast.LENGTH_SHORT
        ).show()
        selectedFilesAdapter.removeAllFiles(this)
    }

    fun uploadFilesProgressCallback(done: Boolean, fileName: String?, progress: Int?) {
        binding.progress.text = "UPLOAD PROGRESS OF $fileName: % $progress"
        if (done) {
            binding.progress.text = "PROGRESS: WAITING FOR A REQUEST..."
            Toast.makeText(
                this, "Upload Completed", Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun handleLayouts() {
        val serverFilesLayoutManager = LinearLayoutManager(this)
        binding.serverFiles.layoutManager = serverFilesLayoutManager
        val selectedFilesLayoutManager = LinearLayoutManager(this)
        binding.selectedFiles.layoutManager = selectedFilesLayoutManager
        binding.selectedFiles.adapter = selectedFilesAdapter
    }

    private fun handleListeners() {
        binding.retryButton.setOnClickListener {
            getServerStatus(null, true)
        }
        binding.uploadFilesButton.setOnClickListener {
            getServerStatus(::uploadFiles)
        }
        binding.downloadFilesButton.setOnClickListener {
            getServerStatus(::downloadFiles)
        }
        binding.getFilesButton.setOnClickListener {
            getServerStatus(::getFilesFromApi)
        }
        binding.selectAllFilesButton.setOnClickListener {
            selectAllFiles()
        }
        binding.clearSelectedFilesButton.setOnClickListener {
            clearSelectedFiles()
        }
    }

    private fun getServerStatus(callback: (() -> Any)?, isRetry: Boolean = false) {
        binding.swStatus.text = "SERVER STATUS:"
        binding.progressBar.visibility = View.VISIBLE
        binding.retryButton.visibility = View.GONE

        if (isRetry) Toast.makeText(this, "Retrying to connect to server...", Toast.LENGTH_SHORT)
            .show()

        api.getServerStatus {
            isServerOnline = it
            runOnUiThread { setServerStatus() }
            if (callback != null) {
                if (isServerOnline) {
                    callback()
                } else {
                    serverFilesAdapter = ServerFilesRecyclerAdapter(listOf(), selectedFilesAdapter)
                    binding.serverFiles.adapter = serverFilesAdapter
                    clearSelectedFiles()

                    binding.downloadFilesButton.visibility = View.GONE
                    binding.selectedFilesCard.visibility = View.GONE
                    binding.selectAllFilesButton.visibility = View.GONE
                    binding.clearSelectedFilesButton.visibility = View.GONE

                    val annotation =
                        (callback as KFunction<*>).annotations.firstOrNull { annotation ->
                            annotation is FunctionInfo
                        } as FunctionInfo?
                    val errorMessage =
                        if (annotation?.description.isNullOrBlank()) "Server connection has failed. Please make sure server is online."
                        else "\"${annotation!!.description}\" has failed. Please make sure server is online."

                    Toast.makeText(
                        this, errorMessage, Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    @FunctionInfo("Get Files")
    private fun getFilesFromApi() {
        api.getFileInfoList { fileInfoList ->
            serverFilesAdapter = ServerFilesRecyclerAdapter(fileInfoList, selectedFilesAdapter)
            binding.serverFiles.adapter = serverFilesAdapter

            binding.downloadFilesButton.visibility = View.VISIBLE
            binding.selectedFilesCard.visibility = View.VISIBLE
            binding.selectAllFilesButton.visibility = View.VISIBLE
            binding.clearSelectedFilesButton.visibility = View.VISIBLE
        }
    }

    @FunctionInfo("Upload Files")
    private fun uploadFiles() {
        if (handlePermission.allRuntimePermissionsGranted()) {
            filePicker.getFilePickerLauncher.launch("*/*")
        } else {
            handlePermission.getRuntimePermissions()
        }
    }

    @FunctionInfo("Download Files")
    private fun downloadFiles() {
        if (selectedFilesAdapter.selectedFiles.size > 0) {
            if (handlePermission.allRuntimePermissionsGranted()) {
                val saveFolderPath = getDownloadsDirectory().let { "$it/ChaTransfer" }
                val directory = File(saveFolderPath)
                val directoryExists = directory.exists()

                if (directoryExists) {
                    DownloadFilesProgressHolder.saveFolderPath = saveFolderPath
                    HandleNotification.startDownloadFiles(
                        this, selectedFilesAdapter.selectedFiles
                    )

                } else {
                    directory.mkdirs().let { isDirectoryCreated ->
                        if (isDirectoryCreated) {
                            DownloadFilesProgressHolder.saveFolderPath = saveFolderPath
                            HandleNotification.startDownloadFiles(
                                this, selectedFilesAdapter.selectedFiles
                            )

                        } else {
                            Toast.makeText(
                                this,
                                "There is an error while download directory has been creating.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            } else {
                handlePermission.getRuntimePermissions()
            }
        } else {
            Toast.makeText(this, "There is no file selected.", Toast.LENGTH_LONG).show()
        }
    }

    private fun selectAllFiles() {
        if (serverFilesAdapter !== null) {
            selectedFilesAdapter.selectAllFiles(this, ArrayList(serverFilesAdapter!!.fileInfoList))
        } else {
            Toast.makeText(
                this, "You should get files from server first.", Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun clearSelectedFiles() {
        selectedFilesAdapter.removeAllFiles(this)
    }

    private fun setServerStatus() {
        binding.retryButton.visibility = View.GONE.takeIf { isServerOnline } ?: View.VISIBLE

        val spannableStringBuilder = SpannableStringBuilder()

        val text1 = "SERVER STATUS: "
        val spannableString1 = SpannableString(text1)
        val color1 = ContextCompat.getColor(this, R.color.dark_blue)
        val colorSpan1 = ForegroundColorSpan(color1)
        spannableString1.setSpan(colorSpan1, 0, text1.length, 0)
        spannableStringBuilder.append(spannableString1)

        val text2 = "ONLINE".takeIf { isServerOnline } ?: "OFFLINE"
        val spannableString2 = SpannableString(text2)
        val color2 = ContextCompat.getColor(this, R.color.cyan).takeIf { isServerOnline }
            ?: ContextCompat.getColor(this, R.color.red)
        val colorSpan2 = ForegroundColorSpan(color2)
        spannableString2.setSpan(colorSpan2, 0, text2.length, 0)
        spannableStringBuilder.append(spannableString2)

        binding.progressBar.visibility = View.GONE
        binding.swStatus.text = spannableStringBuilder
    }

    private fun setAppName() {
        val spannableStringBuilder = SpannableStringBuilder()

        val text1 = "CHA"
        val spannableString1 = SpannableString(text1)
        val color1 = ContextCompat.getColor(this, R.color.dark_blue)
        val colorSpan1 = ForegroundColorSpan(color1)
        spannableString1.setSpan(colorSpan1, 0, text1.length, 0)
        spannableStringBuilder.append(spannableString1)

        val text2 = "TRANSFER"
        val spannableString2 = SpannableString(text2)
        val color2 = ContextCompat.getColor(this, R.color.red)
        val colorSpan2 = ForegroundColorSpan(color2)
        spannableString2.setSpan(colorSpan2, 0, text2.length, 0)
        spannableStringBuilder.append(spannableString2)

        binding.pageTitle.text = spannableStringBuilder
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.navigationBars())
    }

    @Retention(AnnotationRetention.RUNTIME)
    @Target(AnnotationTarget.FUNCTION)
    annotation class FunctionInfo(val description: String)
}