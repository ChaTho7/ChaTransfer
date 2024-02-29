package com.chatho.chatransfer.adapter

import android.content.Context
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
        holder.binding.selectedFileSize.text = "${
            Utils.toDouble(
                (selectedFiles[position].fileSize.toDouble()) / (1024.0 * 1024.0), 2
            )
        } MB"
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
            selectedFiles.clear()
            selectedFiles.addAll(fileInfoList)
            println("All files added to download list.")
            notifyDataSetChanged()
            mainActivityBinding.clearSelectedFilesButton.text =
                "CLEAR SELECTED (${itemCount}) FILES (${getFilesTotalSize()} MB)"
        } else {
            Toast.makeText(context, "There is no file fetched from server.", Toast.LENGTH_SHORT)
                .show()
        }
    }

    fun removeAllFiles(context: Context) {
        if (selectedFiles.size > 0) {
            selectedFiles.clear()
            println("All files removed from download list.")
            notifyDataSetChanged()
            mainActivityBinding.clearSelectedFilesButton.text = "CLEAR SELECTED FILES"
        } else {
            Toast.makeText(context, "There is no file selected.", Toast.LENGTH_SHORT).show()
        }
    }

    fun addNewSelectedFile(fileInfo: GetFileInfoListResponse) {
        if (!selectedFiles.any { it.filename == fileInfo.filename }) {
            selectedFiles.add(0, fileInfo)
            println("${fileInfo.filename} added to download list.")
            notifyDataSetChanged()
            mainActivityBinding.clearSelectedFilesButton.text =
                "CLEAR SELECTED (${itemCount}) FILES (${getFilesTotalSize()} MB)"
        }
    }

    private fun removeSelectedFile(index: Int) {
        val fileInfo = selectedFiles.removeAt(index)
        println("${fileInfo.filename} removed from download list.")
        notifyItemRemoved(index)
        notifyItemRangeChanged(index, selectedFiles.size)
        mainActivityBinding.clearSelectedFilesButton.text =
            "CLEAR SELECTED${if (itemCount > 0) " (${itemCount}) " else " "}FILES${if (itemCount > 0) " (${getFilesTotalSize()} MB)" else ""}"
    }
}