package com.chatho.chatransfer.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.chatho.chatransfer.databinding.SelectedFilesRecyclerBinding
import kotlin.coroutines.coroutineContext

class SelectedFilesRecyclerAdapter(val selectedFiles: ArrayList<String>) :
    RecyclerView.Adapter<SelectedFilesRecyclerAdapter.SelectedFilesVH>() {

    class SelectedFilesVH(val binding: SelectedFilesRecyclerBinding) :
        RecyclerView.ViewHolder(binding.root) {
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SelectedFilesVH {
        val binding =
            SelectedFilesRecyclerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SelectedFilesVH(binding)
    }

    override fun onBindViewHolder(holder: SelectedFilesVH, position: Int) {
        if (position > 0) {
            val layoutParams = holder.binding.selectedFileFilename.layoutParams as ConstraintLayout.LayoutParams
            layoutParams.topMargin = 10
            holder.binding.selectedFileFilename.layoutParams = layoutParams

            val parentView = holder.binding.root // Change this to your actual parent view type
            parentView.requestLayout()
        }
        holder.binding.selectedFileFilename.text = selectedFiles[position]
        holder.itemView.setOnClickListener {
            removeSelectedFile(position)
        }
    }

    override fun getItemCount(): Int {
        return selectedFiles.size
    }

    fun selectAllFiles(context: Context, filenames: ArrayList<String>) {
        if (filenames.size > 0) {
            selectedFiles.clear()
            selectedFiles.addAll(filenames)
            println("All files added to download list.")
            notifyDataSetChanged()
        } else {
            Toast.makeText(context, "There is no file fetched from server.", Toast.LENGTH_SHORT).show()
        }
    }

    fun removeAllFiles(context: Context) {
        if (selectedFiles.size > 0) {
            selectedFiles.clear()
            println("All files removed from download list.")
            notifyDataSetChanged()
        } else {
            Toast.makeText(context, "There is no file selected.", Toast.LENGTH_SHORT).show()
        }
    }

    fun addNewSelectedFile(filename: String) {
        if (!selectedFiles.contains(filename)) {
            selectedFiles.add(filename)
            println("$filename added to download list.")
            notifyDataSetChanged()
        }
    }

    private fun removeSelectedFile(index: Int) {
        val filename = selectedFiles.removeAt(index)
        println("$filename removed from download list.")
        notifyItemRemoved(index)
        notifyItemRangeChanged(index, selectedFiles.size)
    }
}