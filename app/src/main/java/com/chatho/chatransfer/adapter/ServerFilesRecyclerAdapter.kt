package com.chatho.chatransfer.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.marginTop
import androidx.recyclerview.widget.RecyclerView
import com.chatho.chatransfer.databinding.ServerFilesRecyclerBinding

class ServerFilesRecyclerAdapter(val filenames: ArrayList<String>, private val selectedFilesAdapter: SelectedFilesRecyclerAdapter) :
    RecyclerView.Adapter<ServerFilesRecyclerAdapter.ServerFilesVH>() {

    class ServerFilesVH(val binding: ServerFilesRecyclerBinding) : RecyclerView.ViewHolder(binding.root) {
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ServerFilesVH {
        val binding = ServerFilesRecyclerBinding.inflate(LayoutInflater.from(parent.context),parent,false)
        return ServerFilesVH(binding)
    }

    override fun onBindViewHolder(holder: ServerFilesVH, position: Int) {
        if (position > 0) {
            val layoutParams = holder.binding.serverFileFilename.layoutParams as ConstraintLayout.LayoutParams
            layoutParams.topMargin = 10
            holder.binding.serverFileFilename.layoutParams = layoutParams

            val parentView = holder.binding.root // Change this to your actual parent view type
            parentView.requestLayout()
        }
        holder.binding.serverFileFilename.text = filenames[position]
        holder.itemView.setOnClickListener {
            selectedFilesAdapter.addNewSelectedFile(filenames[position])
        }
    }

    override fun getItemCount(): Int {
        return filenames.size
    }
}