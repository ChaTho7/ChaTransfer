package com.chatho.chatransfer

import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.chatho.chatransfer.adapter.SelectedFilesRecyclerAdapter
import com.chatho.chatransfer.adapter.ServerFilesRecyclerAdapter
import com.chatho.chatransfer.api.FlaskAPI
import com.chatho.chatransfer.databinding.ActivityMainBinding
import com.chatho.chatransfer.util.HandleFileSystem
import com.chatho.chatransfer.util.HandlePermission
import java.io.File

class MainActivity : AppCompatActivity() {
    private var api = FlaskAPI(this)
    private var permission = HandlePermission
    private var file = HandleFileSystem()
    private val filePicker = HandleFileSystem(this, api) { done, fileName, progress ->
        runOnUiThread { filePickerCallback(done, fileName, progress) }
    }
    private lateinit var binding: ActivityMainBinding
    private lateinit var sharedPreferences: SharedPreferences
    private var isServerOnline: Boolean = false
    private val selectedFilesAdapter = SelectedFilesRecyclerAdapter(arrayListOf())
    private var serverFilesAdapter: ServerFilesRecyclerAdapter? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedPreferences = this.getSharedPreferences("com.chatho.chatransfer", MODE_PRIVATE)
        api.getServerStatus {
            isServerOnline = it
            runOnUiThread { setServerStatus() }
        }

        // VISIBILITIES
        binding.retryButton.visibility = View.INVISIBLE
        binding.downloadFilesButton.visibility = View.GONE
        binding.selectedFilesCard.visibility = View.GONE
        binding.selectAllFilesButton.visibility = View.GONE
        binding.clearSelectedFilesButton.visibility = View.GONE

        // LAYOUTS
        val serverFilesLayoutManager = LinearLayoutManager(this)
        binding.serverFiles.layoutManager = serverFilesLayoutManager
        val selectedFilesLayoutManager = LinearLayoutManager(this)
        binding.selectedFiles.layoutManager = selectedFilesLayoutManager
        binding.selectedFiles.adapter = selectedFilesAdapter
    }

    fun getServerStatus(view: View) {
        binding.swStatus.text = "SERVER STATUS:"
        binding.progressBar.visibility = View.VISIBLE
        binding.retryButton.visibility = View.INVISIBLE
        api.getServerStatus {
            isServerOnline = it
            runOnUiThread { setServerStatus() }
        }
        Toast.makeText(this, "Retrying to connect to server...", Toast.LENGTH_SHORT).show()
    }

    fun getFiles(view: View) {
        api.getFiles { filenames ->
            serverFilesAdapter = ServerFilesRecyclerAdapter(filenames, selectedFilesAdapter)
            binding.serverFiles.adapter = serverFilesAdapter

            binding.downloadFilesButton.visibility = View.VISIBLE
            binding.selectedFilesCard.visibility = View.VISIBLE
            binding.selectAllFilesButton.visibility = View.VISIBLE
            binding.clearSelectedFilesButton.visibility = View.VISIBLE
        }
    }

    fun uploadFiles(view: View) {
        if (permission.handlePermissions(this)) {
            filePicker.filePickerLauncher.let {
                it!!.launch("*/*")
            }
        }
    }

    fun downloadFiles(view: View) {
        if (selectedFilesAdapter.selectedFiles.size > 0) {
            if (permission.handlePermissions(this)) {

                val saveFolder = file.getDownloadsDirectory().let { "$it/ChaTransfer" }
                val directory = File(saveFolder)
                val directoryExists = directory.exists()

                if (directoryExists) {
                    for (file in selectedFilesAdapter.selectedFiles) {
                        val fileUrl = "/download_file?filename=$file"
                        val progressCallback = { bytesRead: Long, totalBytes: Long ->
                            val progress = (bytesRead.toDouble() / totalBytes * 100).toInt()
                            binding.progress.text = "DOWNLOAD PROGRESS OF $file: % $progress"
                        }

                        api.downloadFiles(fileUrl, "$saveFolder/$file", progressCallback) {
                            binding.progress.text = "PROGRESS: WAITING FOR A REQUEST..."
                            Toast.makeText(
                                this,
                                "$file downloaded",
                                Toast.LENGTH_SHORT
                            ).show()
                            selectedFilesAdapter.removeAllFiles(this)
                        }

                    }
                } else {
                    directory.mkdirs().let { isDirectoryCreated ->
                        if (isDirectoryCreated) {
                            for (file in selectedFilesAdapter.selectedFiles) {
                                val fileUrl = "/download_file?filename=$file"
                                val progressCallback = { bytesRead: Long, totalBytes: Long ->
                                    val progress = (bytesRead.toDouble() / totalBytes * 100).toInt()
                                    binding.progress.text = "DOWNLOAD PROGRESS OF $file: % $progress"
                                }

                                api.downloadFiles(fileUrl, "$saveFolder/$file", progressCallback) {
                                    binding.progress.text = "PROGRESS: WAITING FOR A REQUEST..."
                                    Toast.makeText(
                                        this,
                                        "$file downloaded",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        } else {
                            Toast.makeText(
                                this,
                                "There is an error while download directory has been creating.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }
        } else {
            Toast.makeText(this, "There is no selected files.", Toast.LENGTH_LONG).show()
        }
    }

    fun selectAllFiles(view: View) {
        if (serverFilesAdapter !== null) {
            selectedFilesAdapter.let {
                it.selectAllFiles(this, serverFilesAdapter!!.filenames)
            }
        } else {
            Toast.makeText(
                this,
                "You should get files from server first.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun clearSelectedFiles(view: View) {
        selectedFilesAdapter.let {
            it.removeAllFiles(this)
        }
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

    private fun filePickerCallback(done: Boolean, fileName: String, progress: Int) {
        binding.progress.text = "UPLOAD PROGRESS OF $fileName: % $progress"
        if (done) {
            binding.progress.text = "PROGRESS: WAITING FOR A REQUEST..."
            Toast.makeText(
                this,
                "Upload Completed",
                Toast.LENGTH_LONG
            ).show()
        }
    }

}