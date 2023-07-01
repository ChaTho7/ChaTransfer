package com.chatho.chatransfer.view

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.chatho.chatransfer.R
import com.chatho.chatransfer.adapter.SelectedFilesRecyclerAdapter
import com.chatho.chatransfer.adapter.ServerFilesRecyclerAdapter
import com.chatho.chatransfer.api.FlaskAPI
import com.chatho.chatransfer.holder.MainActivityHolder
import com.chatho.chatransfer.databinding.ActivityMainBinding
import com.chatho.chatransfer.handle.HandleFileSystem
import com.chatho.chatransfer.handle.HandleFileSystem.Companion.getDownloadsDirectory
import com.chatho.chatransfer.handle.HandleNotification
import com.chatho.chatransfer.handle.HandlePermission
import com.chatho.chatransfer.holder.DownloadFilesProgressHolder
import java.io.File

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var api: FlaskAPI
    private lateinit var filePicker: HandleFileSystem
    private var handlePermission = HandlePermission(this)
    private val selectedFilesAdapter = SelectedFilesRecyclerAdapter(arrayListOf())
    private var serverFilesAdapter: ServerFilesRecyclerAdapter? = null
    private var isServerOnline: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        MainActivityHolder.activity = this
        api = FlaskAPI(null)

        filePicker = HandleFileSystem { fileList ->
            HandleNotification.startUploadFiles(this, fileList)
        }

        if (!handlePermission.allRuntimePermissionsGranted()) {
            handlePermission.getRuntimePermissions()
        }

        api.getServerStatus {
            isServerOnline = it
            runOnUiThread { setServerStatus() }
        }

        handleLayouts()
        handleListeners()
    }

    fun downloadFilesProgressCallback(fileName: String, bytesRead: Long, totalBytes: Long) {
        val progress = (bytesRead.toDouble() / totalBytes * 100).toInt()
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
            getServerStatus(null)
        }
        binding.uploadFilesButton.setOnClickListener {
            uploadFiles()
        }
        binding.downloadFilesButton.setOnClickListener {
            downloadFiles()
        }
        binding.getFilesButton.setOnClickListener {
            getFiles()
        }
        binding.selectAllFilesButton.setOnClickListener {
            selectAllFiles()
        }
        binding.clearSelectedFilesButton.setOnClickListener {
            clearSelectedFiles()
        }
    }

    private fun getServerStatus(callback: (() -> Any)?) {
        binding.swStatus.text = "SERVER STATUS:"
        binding.progressBar.visibility = View.VISIBLE
        binding.retryButton.visibility = View.INVISIBLE

        Toast.makeText(this, "Retrying to connect to server...", Toast.LENGTH_SHORT).show()

        api.getServerStatus {
            isServerOnline = it
            runOnUiThread { setServerStatus() }
            if (callback != null && isServerOnline) callback()
        }
    }

    private fun getFiles() {
        if (isServerOnline) {
            api.getFiles { filenames ->
                serverFilesAdapter = ServerFilesRecyclerAdapter(filenames, selectedFilesAdapter)
                binding.serverFiles.adapter = serverFilesAdapter

                binding.downloadFilesButton.visibility = View.VISIBLE
                binding.selectedFilesCard.visibility = View.VISIBLE
                binding.selectAllFilesButton.visibility = View.VISIBLE
                binding.clearSelectedFilesButton.visibility = View.VISIBLE
            }
        } else {
            getServerStatus(::getFiles)
        }
    }

    private fun uploadFiles() {
        if (isServerOnline) {
            if (handlePermission.allRuntimePermissionsGranted()) {
                filePicker.getFilePickerLauncher.launch("*/*")
            } else {
                handlePermission.getRuntimePermissions()
            }
        } else {
            getServerStatus(::uploadFiles)
        }
    }

    private fun downloadFiles() {
        if (isServerOnline) {
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
                Toast.makeText(this, "There is no files selected.", Toast.LENGTH_LONG).show()
            }
        } else {
            getServerStatus(::downloadFiles)
        }
    }

    private fun selectAllFiles() {
        if (serverFilesAdapter !== null) {
            selectedFilesAdapter.selectAllFiles(this, serverFilesAdapter!!.filenames)
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
        binding.retryButton.visibility = View.INVISIBLE.takeIf { isServerOnline } ?: View.VISIBLE

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
}