package com.chatho.chatransfer.adapter

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.chatho.chatransfer.Utils
import com.chatho.chatransfer.api.GetFileInfoListResponse
import com.chatho.chatransfer.databinding.ActivityMainBinding
import com.chatho.chatransfer.databinding.SelectedFilesRecyclerBinding

class SelectedFilesRecyclerAdapter(
    val selectedFiles: ArrayList<GetFileInfoListResponse>,
    private val mainActivityBinding: ActivityMainBinding
) : RecyclerView.Adapter<SelectedFilesRecyclerAdapter.SelectedFilesVH>() {

    class SelectedFilesVH(val binding: SelectedFilesRecyclerBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SelectedFilesVH {
        val binding =
            SelectedFilesRecyclerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SelectedFilesVH(binding)
    }

    override fun onBindViewHolder(holder: SelectedFilesVH, position: Int) {
        if (position > 0) {
            val layoutParams =
                holder.binding.selectedFileFilename.layoutParams as ConstraintLayout.LayoutParams
            layoutParams.topMargin = 10
            holder.binding.selectedFileFilename.layoutParams = layoutParams

            val parentView = holder.binding.root
            parentView.requestLayout()
        }
        holder.binding.selectedFileFilename.text = selectedFiles[position].filename
        val fileSizeText = "${
            Utils.toDouble(
                (selectedFiles[position].fileSize.toDouble()) / (1024.0 * 1024.0), 2
            )
        } MB"
        holder.binding.selectedFileSize.text = fileSizeText
        holder.itemView.setOnClickListener {
            removeSelectedFile(position)
        }
    }

    override fun getItemCount(): Int {
        return selectedFiles.size
    }

    private fun getFilesTotalSize(): Double {
        return Utils.toDouble(
            (selectedFiles.sumOf { it.fileSize.toDouble() }) / (1024.0 * 1024.0), 2
        )
    }

    fun selectAllFiles(context: Context, fileInfoList: ArrayList<GetFileInfoListResponse>) {
        if (fileInfoList.size > 0) {
            val currentSize = selectedFiles.size
            selectedFiles.clear()
            notifyItemRangeRemoved(0, currentSize)

            selectedFiles.addAll(fileInfoList)
            Log.i("SELECTED FILES RECYCLER ADAPTER", "ALL FILES ARE SELECTED TO DOWNLOAD LIST")
            notifyItemRangeInserted(0, selectedFiles.size)

            val buttonText = "CLEAR SELECTED (${itemCount}) FILES (${getFilesTotalSize()} MB)"
            mainActivityBinding.clearSelectedFilesButton.text = buttonText
        } else {
            Toast.makeText(context, "There is no file fetched from server.", Toast.LENGTH_SHORT)
                .show()
        }
    }

    fun removeAllFiles(context: Context) {
        val size = selectedFiles.size
        if (size > 0) {
            selectedFiles.clear()
            Log.i("SELECTED FILES RECYCLER ADAPTER", "ALL FILES ARE REMOVED FROM DOWNLOAD LIST")
            notifyItemRangeRemoved(0, size)

            val buttonText = "CLEAR SELECTED FILES"
            mainActivityBinding.clearSelectedFilesButton.text = buttonText
        } else {
            Toast.makeText(context, "There is no file selected.", Toast.LENGTH_SHORT).show()
        }
    }

    fun addNewSelectedFile(fileInfo: GetFileInfoListResponse) {
        if (!selectedFiles.any { it.filename == fileInfo.filename }) {
            selectedFiles.add(0, fileInfo)
            Log.i(
                "SELECTED FILES RECYCLER ADAPTER", "${fileInfo.filename} IS ADDED TO DOWNLOAD LIST"
            )
            notifyItemInserted(0)
            notifyItemRangeChanged(0, selectedFiles.size)

            val buttonText = "CLEAR SELECTED (${itemCount}) FILES (${getFilesTotalSize()} MB)"
            mainActivityBinding.clearSelectedFilesButton.text = buttonText
        }
    }

    private fun removeSelectedFile(index: Int) {
        val fileInfo = selectedFiles.removeAt(index)
        Log.i(
            "SELECTED FILES RECYCLER ADAPTER", "${fileInfo.filename} IS REMOVED FROM DOWNLOAD LIST"
        )
        notifyItemRemoved(index)
        notifyItemRangeChanged(index, selectedFiles.size - index)

        val buttonText =
            "CLEAR SELECTED${if (itemCount > 0) " (${itemCount}) " else " "}FILES${if (itemCount > 0) " (${getFilesTotalSize()} MB)" else ""}"
        mainActivityBinding.clearSelectedFilesButton.text = buttonText
    }
}