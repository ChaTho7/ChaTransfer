package com.chatho.chatransfer.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.chatho.chatransfer.api.GetFileInfoListResponse
import com.chatho.chatransfer.databinding.ServerFilesRecyclerBinding

class ServerFilesRecyclerAdapter(
    val fileInfoList: List<GetFileInfoListResponse>,
    private val selectedFilesAdapter: SelectedFilesRecyclerAdapter
) : RecyclerView.Adapter<ServerFilesRecyclerAdapter.ServerFilesVH>() {

    class ServerFilesVH(val binding: ServerFilesRecyclerBinding) :
        RecyclerView.ViewHolder(binding.root) {}

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ServerFilesVH {
        val binding =
            ServerFilesRecyclerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ServerFilesVH(binding)
    }

    override fun onBindViewHolder(holder: ServerFilesVH, position: Int) {
        if (position > 0) {
            val layoutParams =
                holder.binding.serverFileFilename.layoutParams as ConstraintLayout.LayoutParams
            layoutParams.topMargin = 10
            holder.binding.serverFileFilename.layoutParams = layoutParams

            val parentView = holder.binding.root
            parentView.requestLayout()
        }
        holder.binding.serverFileFilename.text = fileInfoList[position].filename
        holder.itemView.setOnClickListener {
            selectedFilesAdapter.addNewSelectedFile(fileInfoList[position])
        }
    }

    override fun getItemCount(): Int {
        return fileInfoList.size
    }
}